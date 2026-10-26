-- Auto customer codes (C001, C002...) safe under concurrency.
create function set_customer_code() returns trigger language plpgsql as $$
declare n int; begin
  if new.customer_code is null or new.customer_code = '' then
    perform pg_advisory_xact_lock(hashtext(new.farm_id::text));
    select coalesce(max(substring(customer_code from 2)::int),0)+1 into n
      from customers where farm_id=new.farm_id and customer_code ~ '^C[0-9]+$';
    new.customer_code := 'C' || lpad(n::text,3,'0');
  end if; return new; end $$;
create trigger customers_code before insert on customers for each row execute function set_customer_code();

-- Initial administrator setup is allowed ONLY while no farm exists.
create or replace function bootstrap_farm(p_name text) returns uuid language plpgsql security definer set search_path=public as $$
declare f uuid; begin
  if auth.uid() is null then raise exception 'Not authenticated'; end if;
  if exists(select 1 from farms) then raise exception 'Farm is already set up. Ask the administrator to add your account.'; end if;
  insert into profiles(id) values (auth.uid()) on conflict do nothing;
  insert into farms(name) values (p_name) returning id into f;
  insert into farm_memberships(farm_id,user_id,role,can_record_payments,can_view_finance) values (f,auth.uid(),'admin',true,true);
  insert into farm_settings(farm_id) values (f); return f; end $$;

-- Admin adds/updates staff by email (the user must already exist in Supabase Auth).
create function add_member_by_email(p_farm uuid, p_email text, p_role app_role, p_pay boolean, p_fin boolean) returns void
language plpgsql security definer set search_path=public as $$
declare u uuid; begin
  if not has_role(p_farm, array['admin']::app_role[]) then raise exception 'Not authorized'; end if;
  select id into u from auth.users where lower(email)=lower(p_email);
  if u is null then raise exception 'No account with that email. Create the user in Supabase Auth first.'; end if;
  insert into profiles(id, full_name) values (u, p_email) on conflict (id) do nothing;
  insert into farm_memberships(farm_id,user_id,role,can_record_payments,can_view_finance) values (p_farm,u,p_role,p_pay,p_fin)
  on conflict (farm_id,user_id) do update set role=excluded.role,
    can_record_payments=excluded.can_record_payments, can_view_finance=excluded.can_view_finance; end $$;

-- Only signed-in users may call these RPCs.
revoke all on function register_animal(uuid,species_t,uuid,uuid,jsonb), transfer_animal(uuid,uuid,date),
  bootstrap_farm(text), add_member_by_email(uuid,text,app_role,boolean,boolean) from public, anon;
grant execute on function register_animal(uuid,species_t,uuid,uuid,jsonb), transfer_animal(uuid,uuid,date),
  bootstrap_farm(text), add_member_by_email(uuid,text,app_role,boolean,boolean) to authenticated;
