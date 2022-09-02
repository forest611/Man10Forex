create table man10_binary.log
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

