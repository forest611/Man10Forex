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
    position_id varchar(36)                        not null,
    player      varchar(16)                        null,
    uuid        varchar(36)                        null,
    lots        double                             null,
    buy         tinyint  default 0                 null,
    sell        tinyint  default 0                 null,
    `exit`      tinyint  default 0                 null,
    entry_price double                             null,
    exit_price  double                             null,
    profit      double                             null,
    entry_date  datetime default CURRENT_TIMESTAMP null,
    exit_date   datetime                           null,
    sl_price    double   default 0                 null,
    tp_price    double   default 0                 null
);

create index position_table_exit_uuid_index
    on man10_binary.position_table (`exit`, uuid);

