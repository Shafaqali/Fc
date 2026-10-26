-- Helpers (SECURITY DEFINER avoids RLS recursion on farm_memberships)
create function is_member(f uuid) returns boolean language sql stable security definer set search_path=public as
$$ select exists(select 1 from farm_memberships where farm_id=f and user_id=auth.uid()) $$;
create function has_role(f uuid, r app_role[]) returns boolean language sql stable security definer set search_path=public as
$$ select exists(select 1 from farm_memberships where farm_id=f and user_id=auth.uid() and role = any(r)) $$;
create function can_pay(f uuid) returns boolean language sql stable security definer set search_path=public as
$$ select exists(select 1 from farm_memberships where farm_id=f and user_id=auth.uid()
  and (role='admin' or (role='staff' and can_record_payments))) $$;
create function can_see_finance(f uuid) returns boolean language sql stable security definer set search_path=public as
$$ select exists(select 1 from farm_memberships where farm_id=f and user_id=auth.uid()
  and (role='admin' or can_view_finance)) $$;

do $$ declare t text; begin
 foreach t in array array['farms','profiles','farm_memberships','farm_settings','customers','animal_counters','animals',
  'animal_ownership_history','weight_records','care_plans','care_assignments','invoices','invoice_items','payments',
  'payment_allocations','vaccination_records','treatment_records','animal_health_records','reminders','animal_qr_tokens','activity_logs']
 loop execute format('alter table %I enable row level security', t); end loop; end $$;

-- Operational tables: members read; admin+staff write; only admin deletes.
do $$ declare t text; begin
 foreach t in array array['customers','animals','animal_ownership_history','weight_records','care_assignments',
  'vaccination_records','treatment_records','animal_health_records','reminders','animal_qr_tokens']
 loop
  execute format('create policy %1$s_read on %1$I for select using (is_member(farm_id))', t);
  execute format('create policy %1$s_ins on %1$I for insert with check (has_role(farm_id, array[''admin'',''staff'']::app_role[]))', t);
  execute format('create policy %1$s_upd on %1$I for update using (has_role(farm_id, array[''admin'',''staff'']::app_role[]))', t);
  execute format('create policy %1$s_del on %1$I for delete using (has_role(farm_id, array[''admin'']::app_role[]))', t);
 end loop; end $$;

-- Finance tables: read needs finance permission; writes admin-only (payments: can_pay).
do $$ declare t text; begin
 foreach t in array array['care_plans','invoices','invoice_items','payment_allocations']
 loop
  execute format('create policy %1$s_read on %1$I for select using (can_see_finance(farm_id))', t);
  execute format('create policy %1$s_write on %1$I for all using (has_role(farm_id, array[''admin'']::app_role[])) with check (has_role(farm_id, array[''admin'']::app_role[]))', t);
 end loop; end $$;
create policy payments_read on payments for select using (can_see_finance(farm_id));
create policy payments_ins  on payments for insert with check (can_pay(farm_id));

create policy farms_read on farms for select using (is_member(id));
create policy farms_upd  on farms for update using (has_role(id, array['admin']::app_role[]));
create policy settings_read on farm_settings for select using (is_member(farm_id));
create policy settings_upd  on farm_settings for update using (has_role(farm_id, array['admin']::app_role[]));
create policy members_read on farm_memberships for select using (is_member(farm_id));
create policy members_admin on farm_memberships for all using (has_role(farm_id, array['admin']::app_role[]))
  with check (has_role(farm_id, array['admin']::app_role[]));
create policy profiles_read on profiles for select using (id = auth.uid() or exists(
  select 1 from farm_memberships a join farm_memberships b on a.farm_id=b.farm_id
  where a.user_id=auth.uid() and b.user_id=profiles.id));
create policy profiles_upd on profiles for update using (id = auth.uid());
create policy logs_read on activity_logs for select using (has_role(farm_id, array['admin']::app_role[]));
-- animal_counters: no policies = reachable only through the SECURITY DEFINER functions below.

-- Append-only history, enforced even against admins.
create function forbid_mutation() returns trigger language plpgsql as $$ begin raise exception 'Records are append-only'; end $$;
create trigger weight_append_only before update or delete on weight_records for each row execute function forbid_mutation();
create trigger log_append_only before update or delete on activity_logs for each row execute function forbid_mutation();

create function audit() returns trigger language plpgsql security definer set search_path=public as $$
declare r record; begin
  r := coalesce(new, old);
  insert into activity_logs(farm_id,actor,table_name,row_id,action,old_data,new_data)
  values (r.farm_id, auth.uid(), tg_table_name, r.id, tg_op,
    case when tg_op<>'INSERT' then to_jsonb(old) end, case when tg_op<>'DELETE' then to_jsonb(new) end);
  return r; end $$;
do $$ declare t text; begin
 foreach t in array array['customers','animals','weight_records','vaccination_records','treatment_records','invoices','payments','care_assignments','animal_qr_tokens']
 loop execute format('create trigger audit_%1$s after insert or update or delete on %1$I for each row execute function audit()', t); end loop; end $$;

-- Optimistic concurrency: stale client versions are rejected (conflict detection for offline sync).
create function bump_version() returns trigger language plpgsql as $$ begin
  if new.version <> old.version then raise exception 'CONFLICT: record changed on server (version %)', old.version using errcode='40001'; end if;
  new.version := old.version + 1; new.updated_at := now(); return new; end $$;
create trigger animals_version before update on animals for each row execute function bump_version();

-- Atomic, idempotent registration: allocates ID under row lock, writes ownership + QR token.
create function register_animal(p_farm uuid, p_species species_t, p_customer uuid, p_op uuid, p jsonb)
returns animals language plpgsql security definer set search_path=public as $$
declare v_n int; v_prefix text; a animals; begin
  if not has_role(p_farm, array['admin','staff']::app_role[]) then raise exception 'Not authorized'; end if;
  select * into a from animals where client_op_id = p_op; if found then return a; end if;
  insert into animal_counters(farm_id,species) values (p_farm,p_species) on conflict do nothing;
  update animal_counters set last_value = last_value + 1 where farm_id=p_farm and species=p_species returning last_value into v_n;
  select id_prefixes->>p_species::text into v_prefix from farm_settings where farm_id=p_farm;
  insert into animals(farm_id,animal_code,species,name,breed,gender,date_of_birth,estimated_age_months,arrival_date,
    current_customer_id,identification_notes,health_notes,remarks,client_op_id,created_by)
  values (p_farm, v_prefix || lpad(v_n::text,3,'0'), p_species, p->>'name', p->>'breed', p->>'gender',
    nullif(p->>'date_of_birth','')::date, nullif(p->>'estimated_age_months','')::int, nullif(p->>'arrival_date','')::date,
    p_customer, p->>'identification_notes', p->>'health_notes', p->>'remarks', p_op, auth.uid())
  returning * into a;
  insert into animal_ownership_history(farm_id,animal_id,customer_id,effective_from) values (p_farm,a.id,p_customer,coalesce(a.arrival_date,current_date));
  insert into animal_qr_tokens(farm_id,animal_id) values (p_farm,a.id);
  return a; end $$;

-- Transfer closes the current ownership period and opens a new one; history is preserved.
create function transfer_animal(p_animal uuid, p_new_customer uuid, p_effective date) returns void
language plpgsql security definer set search_path=public as $$
declare a animals; begin
  select * into a from animals where id=p_animal for update;
  if not has_role(a.farm_id, array['admin']::app_role[]) then raise exception 'Not authorized'; end if;
  update animal_ownership_history set effective_to = p_effective - 1 where animal_id=p_animal and effective_to is null;
  insert into animal_ownership_history(farm_id,animal_id,customer_id,effective_from) values (a.farm_id,p_animal,p_new_customer,p_effective);
  update animals set current_customer_id=p_new_customer where id=p_animal; end $$;

-- QR token only locates a record; RLS still decides what the caller can read.
create function resolve_qr(p_token text) returns uuid language sql stable security invoker as
$$ select animal_id from animal_qr_tokens where token=p_token and revoked_at is null $$;

-- First signed-in user creates a farm and becomes administrator.
create function bootstrap_farm(p_name text) returns uuid language plpgsql security definer set search_path=public as $$
declare f uuid; begin
  if auth.uid() is null then raise exception 'Not authenticated'; end if;
  if exists(select 1 from farm_memberships where user_id=auth.uid()) then raise exception 'Already a member of a farm'; end if;
  insert into profiles(id) values (auth.uid()) on conflict do nothing;
  insert into farms(name) values (p_name) returning id into f;
  insert into farm_memberships(farm_id,user_id,role,can_record_payments,can_view_finance) values (f,auth.uid(),'admin',true,true);
  insert into farm_settings(farm_id) values (f); return f; end $$;
