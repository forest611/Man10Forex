create table log
(
    id     int auto_increment,
    player varchar(16)            not null,
    uuid   varchar(36)            not null,
    bet    double                 not null,
    payout double   default 0     not null,
    date   datetime default now() not null,
    constraint key_name
        primary key (id)
);

create table if not exists position_table
(
    id          int auto_increment
        primary key,
    position_id varchar(36)                          not null,
    player      varchar(16)                          null,
    uuid        varchar(36)                          null,
    lots        double                               null,
    buy         tinyint    default 0                 null,
    sell        tinyint    default 0                 null,
    `exit`      tinyint    default 0                 null,
    symbol      varchar(8)                           null,
    entry_price double                               null,
    exit_price  double                               null,
    profit      double                               null,
    entry_date  datetime   default CURRENT_TIMESTAMP null,
    exit_date   datetime                             null,
    sl_price    double     default 0                 null,
    tp_price    double     default 0                 null
);

create index position_table_exit_uuid_index
    on man10_binary.position_table (`exit`, uuid);

create table if not exists pending_table
(
    id          int auto_increment
        primary key,
    player      varchar(16)                        null,
    uuid        varchar(36)                        null,
    lots        double                             null,
    order_type  varchar(16)                        null,
    order_price double                             null,
    tp_price    double                             null,
    sl_price    double                             null,
    status      varchar(16)                        null,
    symbol      varchar(8)                         null,
    order_date  datetime default CURRENT_TIMESTAMP null,
    update_date datetime default CURRENT_TIMESTAMP null
);

create index pending_table_uuid_status_index
    on man10_binary.pending_table (uuid, status);



create table fx_bank
(
	id int auto_increment,
	player varchar(16) not null,
	uuid varchar(36) not null,
	balance double default 0.0 not null,
	constraint user_bank_pk
		primary key (id)
);

create index fx_bank_id_uuid_player_index
	on fx_bank (id, uuid, player);

create table bank_log
(
	id int auto_increment,
	player varchar(16) not null,
	uuid varchar(36) not null,
	plugin_name varchar(16) null,
	amount double default 0 not null,
	note varchar(64) null,
	display_note varchar(64) null,
	server varchar(16) null,
	deposit boolean default true null,
	date datetime default now() not null,
	constraint money_log_pk
		primary key (id)
);