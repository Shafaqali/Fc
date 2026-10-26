-- FarmCare core schema. Run in Supabase SQL editor or `supabase db push`.
create extension if not exists pgcrypto;
create extension if not exists btree_gist;

create type app_role as enum ('admin','staff','viewer');
create type species_t as enum ('goat','buffalo','sheep');
create type animal_status as enum ('active','returned','archived');
create type vacc_status as enum ('scheduled','completed','cancelled');
create type billing_unit as enum ('daily','monthly','custom');

create table farms (
  id uuid primary key default gen_random_uuid(),
  name text not null, details text, logo_path text,
  created_at timestamptz not null default now());

create table profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  full_name text, phone text, created_at timestamptz not null default now());

create table farm_memberships (
  farm_id uuid references farms(id) on delete cascade,
  user_id uuid references auth.users(id) on delete cascade,
  role app_role not null,
  can_record_payments boolean not null default false,
  can_view_finance boolean not null default false,
  primary key (farm_id, user_id));

create table farm_settings (
  farm_id uuid primary key references farms(id) on delete cascade,
  currency text not null default 'INR',
  date_format text not null default 'dd/MM/yyyy',
  weight_tracking jsonb not null default '{"goat":true,"sheep":true,"buffalo":false}',
  id_prefixes jsonb not null default '{"goat":"G","sheep":"S","buffalo":"B"}',
  reminder_days_before int[] not null default '{7,3,1}',
  billing_rules jsonb not null default '{"partial_month":"daily_proration"}',
  payment_methods text[] not null default '{cash,upi,bank_transfer}');

create table customers (
  id uuid primary key default gen_random_uuid(),
  farm_id uuid not null references farms(id) on delete cascade,
  customer_code text not null, full_name text not null,
  phone text not null, alt_phone text, address text, notes text,
  status text not null default 'active' check (status in ('active','inactive')),
  registered_on date not null default current_date,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (farm_id, customer_code), unique (farm_id, phone, full_name));

-- Concurrency-safe ID allocation: one counter row per farm+species, row-locked on use.
create table animal_counters (
  farm_id uuid references farms(id) on delete cascade,
  species species_t, last_value int not null default 0,
  primary key (farm_id, species));

create table animals (
  id uuid primary key default gen_random_uuid(),
  farm_id uuid not null references farms(id) on delete cascade,
  animal_code text not null, species species_t not null,
  name text, photo_path text, breed text, gender text,
  date_of_birth date, estimated_age_months int,
  registered_on date not null default current_date,
  arrival_date date, return_date date,
  current_customer_id uuid not null references customers(id),
  weight_tracking_override boolean,  -- null = follow species default
  identification_notes text, health_notes text, remarks text,
  status animal_status not null default 'active',
  client_op_id uuid unique,          -- offline idempotency
  version int not null default 1,    -- optimistic concurrency
  created_by uuid references auth.users(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (farm_id, animal_code));
create index on animals (farm_id, species, status);
create index on animals (current_customer_id);

create table animal_ownership_history (
  id uuid primary key default gen_random_uuid(),
  farm_id uuid not null references farms(id) on delete cascade,
  animal_id uuid not null references animals(id),
  customer_id uuid not null references customers(id),
  effective_from date not null, effective_to date,
  check (effective_to is null or effective_to >= effective_from),
  exclude using gist (animal_id with =,
    daterange(effective_from, coalesce(effective_to + 1, 'infinity')) with &&));

create table weight_records (
  id uuid primary key default gen_random_uuid(),
  farm_id uuid not null references farms(id) on delete cascade,
  animal_id uuid not null references animals(id),
  weight_kg numeric(7,2) not null check (weight_kg > 0),  -- missing weight = no row, never 0
  measured_on date not null, notes text,
  recorded_by uuid references auth.users(id),
  client_op_id uuid unique,
  created_at timestamptz not null default now());
create index on weight_records (animal_id, measured_on desc, created_at desc);

create table care_plans (
  id uuid primary key default gen_random_uuid(),
  farm_id uuid not null references farms(id) on delete cascade,
  name text not null, species species_t,
  customer_id uuid references customers(id),
  rate numeric(12,2) not null check (rate >= 0),
  unit billing_unit not null default 'daily',
  effective_from date not null, effective_to date,
  created_at timestamptz not null default now());

create table care_assignments (
  id uuid primary key default gen_random_uuid(),
  farm_id uuid not null references farms(id) on delete cascade,
  animal_id uuid not null references animals(id),
  care_plan_id uuid references care_plans(id),
  rate_override numeric(12,2) check (rate_override >= 0),
  unit billing_unit not null default 'daily',
  care_start date not null, care_end date,
  check (care_end is null or care_end >= care_start));

create table invoices (
  id uuid primary key default gen_random_uuid(),
  farm_id uuid not null references farms(id) on delete cascade,
  invoice_no text not null, customer_id uuid not null references customers(id),
  period_start date not null, period_end date not null,
  subtotal numeric(12,2) not null default 0,
  discount numeric(12,2) not null default 0,
  adjustment numeric(12,2) not null default 0,
  total numeric(12,2) not null default 0,
  status text not null default 'issued' check (status in ('draft','issued','void')),
  created_by uuid references auth.users(id),
  created_at timestamptz not null default now(),
  unique (farm_id, invoice_no), check (period_end >= period_start));

create table invoice_items (
  id uuid primary key default gen_random_uuid(),
  invoice_id uuid not null references invoices(id) on delete cascade,
  farm_id uuid not null references farms(id) on delete cascade,
  animal_id uuid references animals(id),
  description text not null, quantity numeric(10,2) not null,
  unit_rate numeric(12,2) not null, amount numeric(12,2) not null,
  item_start date, item_end date);
create unique index invoice_items_no_dupe on invoice_items (animal_id, item_start, item_end)
  where animal_id is not null and item_start is not null;

create table payments (
  id uuid primary key default gen_random_uuid(),
  farm_id uuid not null references farms(id) on delete cascade,
  customer_id uuid not null references customers(id),
  amount numeric(12,2) not null check (amount <> 0),  -- negative = refund
  method text not null, reference_no text, paid_on date not null,
  recorded_by uuid references auth.users(id),
  client_op_id uuid unique,
  created_at timestamptz not null default now());

create table payment_allocations (
  id uuid primary key default gen_random_uuid(),
  farm_id uuid not null references farms(id) on delete cascade,
  payment_id uuid not null references payments(id),
  invoice_id uuid not null references invoices(id),
  amount numeric(12,2) not null check (amount > 0));

create table vaccination_records (
  id uuid primary key default gen_random_uuid(),
  farm_id uuid not null references farms(id) on delete cascade,
  animal_id uuid not null references animals(id),
  vaccine_name text not null, status vacc_status not null default 'scheduled',
  scheduled_for date, administered_on date, next_due_date date,
  dose_details text, administered_by text, batch_no text, notes text,
  document_path text, created_by uuid references auth.users(id),
  client_op_id uuid unique,
  created_at timestamptz not null default now(),
  check (status <> 'completed' or administered_on is not null));
create index on vaccination_records (farm_id, status, scheduled_for);

create table treatment_records (
  id uuid primary key default gen_random_uuid(),
  farm_id uuid not null references farms(id) on delete cascade,
  animal_id uuid not null references animals(id),
  observed_on date not null, symptoms text, diagnosis text,
  vet_consulted boolean not null default false,
  treatment_details text, medicine_name text, vet_instructions text,
  start_date date, end_date date, follow_up_date date,
  recovery_notes text, responsible_person text, document_path text,
  created_by uuid references auth.users(id), client_op_id uuid unique,
  created_at timestamptz not null default now());

create table animal_health_records (
  id uuid primary key default gen_random_uuid(),
  farm_id uuid not null references farms(id) on delete cascade,
  animal_id uuid not null references animals(id),
  recorded_on date not null, note text not null,
  created_by uuid references auth.users(id),
  created_at timestamptz not null default now());

create table reminders (
  id uuid primary key default gen_random_uuid(),
  farm_id uuid not null references farms(id) on delete cascade,
  animal_id uuid references animals(id),
  kind text not null check (kind in ('vaccination','follow_up','appointment')),
  source_id uuid, due_on date not null, dismissed_at timestamptz);

create table animal_qr_tokens (
  id uuid primary key default gen_random_uuid(),
  farm_id uuid not null references farms(id) on delete cascade,
  animal_id uuid not null references animals(id),
  token text not null unique default encode(gen_random_bytes(24),'hex'),
  revoked_at timestamptz, created_at timestamptz not null default now());
create unique index one_active_qr on animal_qr_tokens (animal_id) where revoked_at is null;

create table activity_logs (
  id bigint generated always as identity primary key,
  farm_id uuid not null, actor uuid, table_name text not null,
  row_id uuid, action text not null, old_data jsonb, new_data jsonb,
  at timestamptz not null default now());

-- Derived values (current weight, balances) are VIEWS, never stored duplicates.
create view animal_weight_summary with (security_invoker = true) as
select a.id as animal_id,
  (select w.weight_kg from weight_records w where w.animal_id=a.id order by measured_on desc, created_at desc limit 1) as latest_kg,
  (select w.weight_kg from weight_records w where w.animal_id=a.id order by measured_on desc, created_at desc offset 1 limit 1) as previous_kg
from animals a;

create view customer_balances with (security_invoker = true) as
select c.id as customer_id, c.farm_id,
  coalesce((select sum(total) from invoices i where i.customer_id=c.id and i.status='issued'),0) as charged,
  coalesce((select sum(amount) from payments p where p.customer_id=c.id),0) as paid
from customers c;
