CREATE TABLE public.dashes (
  id     SERIAL PRIMARY KEY           NOT NULL,
  color  CHARACTER VARYING(30)        NOT NULL,
  word   CHARACTER VARYING(50) UNIQUE NOT NULL,
  points TEXT                         NOT NULL
);

CREATE TABLE public.account_dashes (
  id        SERIAL PRIMARY KEY NOT NULL,
  accountid INTEGER            NOT NULL,
  dashesid  INTEGER,
  FOREIGN KEY (dashesid) REFERENCES public.dashes (id)
  MATCH SIMPLE ON UPDATE NO ACTION ON DELETE NO ACTION,
  FOREIGN KEY (accountid) REFERENCES public.account (id)
  MATCH SIMPLE ON UPDATE NO ACTION ON DELETE CASCADE
);