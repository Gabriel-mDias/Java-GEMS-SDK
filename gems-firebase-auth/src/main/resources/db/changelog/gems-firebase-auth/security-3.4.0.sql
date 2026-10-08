CREATE SCHEMA IF NOT EXISTS security;
CREATE FUNCTION security.protect_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.dt_fim IS NOT NULL AND NEW.dt_fim IS DISTINCT FROM OLD.dt_fim THEN
  RAISE EXCEPTION 'closed history is immutable' USING ERRCODE = '23514';
 END IF;
 RETURN NEW;
END $$;
CREATE FUNCTION security.protect_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF (NEW.id_usuario,NEW.id_provedor_identidade,NEW.cd_emissor,NEW.cd_sujeito_externo)
  IS DISTINCT FROM (OLD.id_usuario,OLD.id_provedor_identidade,OLD.cd_emissor,OLD.cd_sujeito_externo) THEN
  RAISE EXCEPTION 'identity ownership is immutable' USING ERRCODE = '23514';
 END IF;
 RETURN NEW;
END $$;
CREATE FUNCTION security.protect_alias() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.cd_alias IS DISTINCT FROM OLD.cd_alias THEN
  RAISE EXCEPTION 'alias is immutable' USING ERRCODE = '23514';
 END IF;
 RETURN NEW;
END $$;
CREATE FUNCTION security.protect_role_scope() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF (NEW.cd_acao,NEW.cd_escopo) IS DISTINCT FROM (OLD.cd_acao,OLD.cd_escopo) THEN
  RAISE EXCEPTION 'action scope is immutable' USING ERRCODE = '23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TABLE security.tenant (
 id_tenant UUID PRIMARY KEY, cd_alias VARCHAR(56) NOT NULL UNIQUE,
 nm_tenant VARCHAR(255) NOT NULL, cd_provisionamento VARCHAR(16) NOT NULL DEFAULT 'PENDING'
 CHECK (cd_provisionamento IN ('PENDING','FAILED','READY')), nr_revisao_aplicada BIGINT NOT NULL DEFAULT 0 CHECK(nr_revisao_aplicada>=0),
 cd_erro_provisionamento VARCHAR(64), dt_provisionamento TIMESTAMPTZ,
 CHECK(cd_alias ~ '^[a-z][a-z0-9_]{0,55}$' AND left(cd_alias,3)<>'pg_' AND left(cd_alias,7)<>'tenant_'
 AND cd_alias NOT IN ('public','security','administracao','auditoria','modelo','information_schema','global','tenant_context_required')),
 dt_inicio TIMESTAMPTZ NOT NULL, dt_fim TIMESTAMPTZ, CHECK(dt_fim IS NULL OR dt_fim>=dt_inicio));
CREATE TRIGGER tenant_alias BEFORE UPDATE ON security.tenant FOR EACH ROW EXECUTE FUNCTION security.protect_alias();
CREATE TABLE security.provedor_identidade (
 id_provedor_identidade UUID PRIMARY KEY, cd_provedor VARCHAR(32) NOT NULL UNIQUE CHECK(cd_provedor='FIREBASE'),
 cd_projeto VARCHAR(63) NOT NULL, cd_emissor VARCHAR(255) NOT NULL,
 singleton BOOLEAN NOT NULL DEFAULT TRUE UNIQUE CHECK(singleton),
 CHECK(cd_emissor='https://securetoken.google.com/'||cd_projeto),
 UNIQUE(id_provedor_identidade,cd_emissor),
 UNIQUE(cd_projeto,cd_emissor),
 dt_inicio TIMESTAMPTZ NOT NULL, dt_fim TIMESTAMPTZ, CHECK(dt_fim IS NULL OR dt_fim>=dt_inicio));
CREATE TABLE security.usuario (
 id_usuario UUID PRIMARY KEY, nm_usuario VARCHAR(255) NOT NULL, cd_email VARCHAR(320) NOT NULL CHECK(cd_email=lower(btrim(cd_email))),
 cd_elegibilidade VARCHAR(32) NOT NULL CHECK(cd_elegibilidade IN ('GOOGLE_PENDING','PASSWORD_PROVISIONING','BOUND')),
 cd_uid_planejado VARCHAR(128) UNIQUE, nr_versao_identidade BIGINT NOT NULL DEFAULT 0 CHECK(nr_versao_identidade>=0),
 dt_criacao TIMESTAMPTZ NOT NULL, dt_alteracao TIMESTAMPTZ NOT NULL,
 dt_inicio TIMESTAMPTZ NOT NULL, dt_fim TIMESTAMPTZ, CHECK(dt_fim IS NULL OR dt_fim>=dt_inicio));
CREATE FUNCTION security.protect_project() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF (NEW.cd_projeto,NEW.cd_emissor) IS DISTINCT FROM (OLD.cd_projeto,OLD.cd_emissor) THEN
  RAISE EXCEPTION 'project is immutable' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE FUNCTION security.prevent_history_delete() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'security history cannot be deleted' USING ERRCODE='23514'; END $$;
CREATE TRIGGER provider_project BEFORE UPDATE ON security.provedor_identidade FOR EACH ROW EXECUTE FUNCTION security.protect_project();
CREATE INDEX usuario_google_pending ON security.usuario(cd_email) WHERE cd_elegibilidade='GOOGLE_PENDING' AND dt_fim IS NULL;
CREATE TABLE security.usuario_identidade (
 id_usuario_identidade UUID PRIMARY KEY, id_usuario UUID NOT NULL REFERENCES security.usuario,
 id_provedor_identidade UUID NOT NULL, cd_emissor VARCHAR(255) NOT NULL,
 cd_sujeito_externo VARCHAR(128) NOT NULL CHECK(length(cd_sujeito_externo)>0), cd_email_identidade VARCHAR(320),
 FOREIGN KEY(id_provedor_identidade,cd_emissor) REFERENCES security.provedor_identidade(id_provedor_identidade,cd_emissor),
 UNIQUE(id_provedor_identidade,cd_emissor,cd_sujeito_externo),
 dt_inicio TIMESTAMPTZ NOT NULL, dt_fim TIMESTAMPTZ, CHECK(dt_fim IS NULL OR dt_fim>=dt_inicio));
CREATE UNIQUE INDEX identidade_usuario_aberta ON security.usuario_identidade(id_usuario,id_provedor_identidade,cd_emissor) WHERE dt_fim IS NULL;
CREATE TRIGGER identidade_owner BEFORE UPDATE ON security.usuario_identidade FOR EACH ROW EXECUTE FUNCTION security.protect_identity();
CREATE TABLE security.usuario_tenant (
 id_usuario_tenant UUID PRIMARY KEY, id_usuario UUID NOT NULL REFERENCES security.usuario,
 id_tenant UUID NOT NULL REFERENCES security.tenant, UNIQUE(id_tenant,id_usuario_tenant),
 dt_inicio TIMESTAMPTZ NOT NULL, dt_fim TIMESTAMPTZ, CHECK(dt_fim IS NULL OR dt_fim>=dt_inicio));
CREATE UNIQUE INDEX membership_aberta ON security.usuario_tenant(id_usuario,id_tenant) WHERE dt_fim IS NULL;
CREATE TABLE security.role_seguranca (
 id_role UUID PRIMARY KEY, cd_acao VARCHAR(128) NOT NULL UNIQUE CHECK(cd_acao ~ '^[A-Z][A-Z0-9]*_[A-Z0-9_]+$'),
 cd_escopo VARCHAR(8) NOT NULL CHECK(cd_escopo IN ('GLOBAL','TENANT')), fl_delegavel BOOLEAN NOT NULL DEFAULT FALSE,
 UNIQUE(id_role,cd_escopo), dt_inicio TIMESTAMPTZ NOT NULL, dt_fim TIMESTAMPTZ,
 CHECK(dt_fim IS NULL OR dt_fim>=dt_inicio));
CREATE TRIGGER role_scope BEFORE UPDATE ON security.role_seguranca FOR EACH ROW EXECUTE FUNCTION security.protect_role_scope();
CREATE TABLE security.perfil (
 id_perfil UUID PRIMARY KEY, id_tenant UUID NOT NULL REFERENCES security.tenant,
 cd_perfil VARCHAR(128) NOT NULL, nm_perfil VARCHAR(255) NOT NULL, ds_perfil TEXT,
 UNIQUE(id_tenant,id_perfil), dt_inicio TIMESTAMPTZ NOT NULL, dt_fim TIMESTAMPTZ,
 CHECK(dt_fim IS NULL OR dt_fim>=dt_inicio));
CREATE UNIQUE INDEX perfil_aberto ON security.perfil(id_tenant,cd_perfil) WHERE dt_fim IS NULL;
CREATE TABLE security.grupo (
 id_grupo UUID PRIMARY KEY, id_tenant UUID NOT NULL REFERENCES security.tenant,
 cd_grupo VARCHAR(128) NOT NULL, nm_grupo VARCHAR(255) NOT NULL, ds_grupo TEXT,
 UNIQUE(id_tenant,id_grupo), dt_inicio TIMESTAMPTZ NOT NULL, dt_fim TIMESTAMPTZ,
 CHECK(dt_fim IS NULL OR dt_fim>=dt_inicio));
CREATE UNIQUE INDEX grupo_aberto ON security.grupo(id_tenant,cd_grupo) WHERE dt_fim IS NULL;
CREATE TABLE security.perfil_role (id_perfil_role UUID PRIMARY KEY, id_tenant UUID NOT NULL REFERENCES security.tenant, id_perfil UUID NOT NULL, id_role UUID NOT NULL, FOREIGN KEY(id_tenant,id_perfil) REFERENCES security.perfil(id_tenant,id_perfil), cd_escopo VARCHAR(8) NOT NULL DEFAULT 'TENANT' CHECK(cd_escopo='TENANT'), FOREIGN KEY(id_role,cd_escopo) REFERENCES security.role_seguranca(id_role,cd_escopo), 
 dt_inicio TIMESTAMPTZ NOT NULL, dt_fim TIMESTAMPTZ, CHECK(dt_fim IS NULL OR dt_fim>=dt_inicio));
CREATE UNIQUE INDEX perfil_role_aberto ON security.perfil_role(id_perfil,id_role) WHERE dt_fim IS NULL;
CREATE INDEX perfil_role_vigencia ON security.perfil_role(id_perfil,dt_inicio,dt_fim);
CREATE TABLE security.usuario_tenant_perfil (id_usuario_tenant_perfil UUID PRIMARY KEY, id_tenant UUID NOT NULL REFERENCES security.tenant, id_usuario_tenant UUID NOT NULL, id_perfil UUID NOT NULL, FOREIGN KEY(id_tenant,id_usuario_tenant) REFERENCES security.usuario_tenant(id_tenant,id_usuario_tenant), FOREIGN KEY(id_tenant,id_perfil) REFERENCES security.perfil(id_tenant,id_perfil), 
 dt_inicio TIMESTAMPTZ NOT NULL, dt_fim TIMESTAMPTZ, CHECK(dt_fim IS NULL OR dt_fim>=dt_inicio));
CREATE UNIQUE INDEX usuario_tenant_perfil_aberto ON security.usuario_tenant_perfil(id_usuario_tenant,id_perfil) WHERE dt_fim IS NULL;
CREATE INDEX usuario_tenant_perfil_vigencia ON security.usuario_tenant_perfil(id_usuario_tenant,dt_inicio,dt_fim);
CREATE TABLE security.usuario_tenant_role (id_usuario_tenant_role UUID PRIMARY KEY, id_tenant UUID NOT NULL REFERENCES security.tenant, id_usuario_tenant UUID NOT NULL, id_role UUID NOT NULL, FOREIGN KEY(id_tenant,id_usuario_tenant) REFERENCES security.usuario_tenant(id_tenant,id_usuario_tenant), cd_escopo VARCHAR(8) NOT NULL DEFAULT 'TENANT' CHECK(cd_escopo='TENANT'), FOREIGN KEY(id_role,cd_escopo) REFERENCES security.role_seguranca(id_role,cd_escopo), 
 dt_inicio TIMESTAMPTZ NOT NULL, dt_fim TIMESTAMPTZ, CHECK(dt_fim IS NULL OR dt_fim>=dt_inicio));
CREATE UNIQUE INDEX usuario_tenant_role_aberto ON security.usuario_tenant_role(id_usuario_tenant,id_role) WHERE dt_fim IS NULL;
CREATE INDEX usuario_tenant_role_vigencia ON security.usuario_tenant_role(id_usuario_tenant,dt_inicio,dt_fim);
CREATE TABLE security.usuario_role_global (id_usuario_role_global UUID PRIMARY KEY, id_usuario UUID NOT NULL, id_role UUID NOT NULL, FOREIGN KEY(id_usuario) REFERENCES security.usuario, cd_escopo VARCHAR(8) NOT NULL DEFAULT 'GLOBAL' CHECK(cd_escopo='GLOBAL'), FOREIGN KEY(id_role,cd_escopo) REFERENCES security.role_seguranca(id_role,cd_escopo), 
 dt_inicio TIMESTAMPTZ NOT NULL, dt_fim TIMESTAMPTZ, CHECK(dt_fim IS NULL OR dt_fim>=dt_inicio));
CREATE UNIQUE INDEX usuario_role_global_aberto ON security.usuario_role_global(id_usuario,id_role) WHERE dt_fim IS NULL;
CREATE INDEX usuario_role_global_vigencia ON security.usuario_role_global(id_usuario,dt_inicio,dt_fim);
CREATE TABLE security.grupo_usuario (id_grupo_usuario UUID PRIMARY KEY, id_tenant UUID NOT NULL REFERENCES security.tenant, id_grupo UUID NOT NULL, id_usuario_tenant UUID NOT NULL, FOREIGN KEY(id_tenant,id_grupo) REFERENCES security.grupo(id_tenant,id_grupo), FOREIGN KEY(id_tenant,id_usuario_tenant) REFERENCES security.usuario_tenant(id_tenant,id_usuario_tenant), 
 dt_inicio TIMESTAMPTZ NOT NULL, dt_fim TIMESTAMPTZ, CHECK(dt_fim IS NULL OR dt_fim>=dt_inicio));
CREATE UNIQUE INDEX grupo_usuario_aberto ON security.grupo_usuario(id_grupo,id_usuario_tenant) WHERE dt_fim IS NULL;
CREATE INDEX grupo_usuario_vigencia ON security.grupo_usuario(id_grupo,dt_inicio,dt_fim);
CREATE TABLE security.grupo_perfil (id_grupo_perfil UUID PRIMARY KEY, id_tenant UUID NOT NULL REFERENCES security.tenant, id_grupo UUID NOT NULL, id_perfil UUID NOT NULL, FOREIGN KEY(id_tenant,id_grupo) REFERENCES security.grupo(id_tenant,id_grupo), FOREIGN KEY(id_tenant,id_perfil) REFERENCES security.perfil(id_tenant,id_perfil), 
 dt_inicio TIMESTAMPTZ NOT NULL, dt_fim TIMESTAMPTZ, CHECK(dt_fim IS NULL OR dt_fim>=dt_inicio));
CREATE UNIQUE INDEX grupo_perfil_aberto ON security.grupo_perfil(id_grupo,id_perfil) WHERE dt_fim IS NULL;
CREATE INDEX grupo_perfil_vigencia ON security.grupo_perfil(id_grupo,dt_inicio,dt_fim);
CREATE TABLE security.grupo_role (id_grupo_role UUID PRIMARY KEY, id_tenant UUID NOT NULL REFERENCES security.tenant, id_grupo UUID NOT NULL, id_role UUID NOT NULL, FOREIGN KEY(id_tenant,id_grupo) REFERENCES security.grupo(id_tenant,id_grupo), cd_escopo VARCHAR(8) NOT NULL DEFAULT 'TENANT' CHECK(cd_escopo='TENANT'), FOREIGN KEY(id_role,cd_escopo) REFERENCES security.role_seguranca(id_role,cd_escopo), 
 dt_inicio TIMESTAMPTZ NOT NULL, dt_fim TIMESTAMPTZ, CHECK(dt_fim IS NULL OR dt_fim>=dt_inicio));
CREATE UNIQUE INDEX grupo_role_aberto ON security.grupo_role(id_grupo,id_role) WHERE dt_fim IS NULL;
CREATE INDEX grupo_role_vigencia ON security.grupo_role(id_grupo,dt_inicio,dt_fim);
CREATE TRIGGER tenant_history BEFORE UPDATE ON security.tenant FOR EACH ROW EXECUTE FUNCTION security.protect_history();
CREATE TRIGGER provedor_identidade_history BEFORE UPDATE ON security.provedor_identidade FOR EACH ROW EXECUTE FUNCTION security.protect_history();
CREATE TRIGGER usuario_identidade_history BEFORE UPDATE ON security.usuario_identidade FOR EACH ROW EXECUTE FUNCTION security.protect_history();
CREATE TRIGGER usuario_tenant_history BEFORE UPDATE ON security.usuario_tenant FOR EACH ROW EXECUTE FUNCTION security.protect_history();
CREATE TRIGGER role_seguranca_history BEFORE UPDATE ON security.role_seguranca FOR EACH ROW EXECUTE FUNCTION security.protect_history();
CREATE TRIGGER perfil_history BEFORE UPDATE ON security.perfil FOR EACH ROW EXECUTE FUNCTION security.protect_history();
CREATE TRIGGER grupo_history BEFORE UPDATE ON security.grupo FOR EACH ROW EXECUTE FUNCTION security.protect_history();
CREATE TRIGGER perfil_role_history BEFORE UPDATE ON security.perfil_role FOR EACH ROW EXECUTE FUNCTION security.protect_history();
CREATE TRIGGER usuario_tenant_perfil_history BEFORE UPDATE ON security.usuario_tenant_perfil FOR EACH ROW EXECUTE FUNCTION security.protect_history();
CREATE TRIGGER usuario_tenant_role_history BEFORE UPDATE ON security.usuario_tenant_role FOR EACH ROW EXECUTE FUNCTION security.protect_history();
CREATE TRIGGER usuario_role_global_history BEFORE UPDATE ON security.usuario_role_global FOR EACH ROW EXECUTE FUNCTION security.protect_history();
CREATE TRIGGER grupo_usuario_history BEFORE UPDATE ON security.grupo_usuario FOR EACH ROW EXECUTE FUNCTION security.protect_history();
CREATE TRIGGER grupo_perfil_history BEFORE UPDATE ON security.grupo_perfil FOR EACH ROW EXECUTE FUNCTION security.protect_history();
CREATE TRIGGER grupo_role_history BEFORE UPDATE ON security.grupo_role FOR EACH ROW EXECUTE FUNCTION security.protect_history();
CREATE TABLE security.evento_seguranca (
 id_evento UUID PRIMARY KEY, cd_evento VARCHAR(64) NOT NULL, id_usuario UUID REFERENCES security.usuario,
 id_tenant UUID REFERENCES security.tenant, dt_evento TIMESTAMPTZ NOT NULL,
 id_ator UUID, cd_tipo_alvo VARCHAR(32) NOT NULL CHECK(cd_tipo_alvo IN ('USER','TENANT','MEMBERSHIP','PROFILE','GROUP',
 'MEMBER_ACTION','PROFILE_ACTION','GROUP_ACTION','GLOBAL_ACTION','MEMBER_PROFILE','GROUP_MEMBER','GROUP_PROFILE','IDENTITY_COMMAND','AUTHENTICATION')),
 id_alvo UUID, id_origem UUID, id_relacionado UUID,
 cd_acao VARCHAR(128), id_correlacao UUID);
CREATE FUNCTION security.append_only_event() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'event is append only' USING ERRCODE='23514'; END $$;
CREATE TRIGGER event_append_only BEFORE UPDATE OR DELETE ON security.evento_seguranca FOR EACH ROW EXECUTE FUNCTION security.append_only_event();
CREATE TABLE security.entrega_evento (
 id_evento UUID PRIMARY KEY REFERENCES security.evento_seguranca,
 cd_estado VARCHAR(8) NOT NULL CHECK(cd_estado IN ('PENDING','CLAIMED','ACKED')),
 cd_claim UUID, dt_lease TIMESTAMPTZ,
 nr_tentativas INTEGER NOT NULL DEFAULT 0 CHECK(nr_tentativas>=0),
 dt_proxima_tentativa TIMESTAMPTZ NOT NULL DEFAULT '-infinity');
CREATE TABLE security.comando_identidade (
 id_comando UUID PRIMARY KEY, id_usuario UUID NOT NULL REFERENCES security.usuario,
 cd_projeto VARCHAR(63) NOT NULL, cd_emissor VARCHAR(255) NOT NULL, cd_uid VARCHAR(128) NOT NULL,
 cd_tipo VARCHAR(8) NOT NULL CHECK(cd_tipo IN ('CREATE','DISABLE','REVOKE','ENABLE')),
 nr_versao BIGINT NOT NULL CHECK(nr_versao>0), cd_idempotencia VARCHAR(128) NOT NULL UNIQUE,
 cd_estado VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK(cd_estado IN ('PENDING','RETRY','CLAIMED','ACKED','OBSOLETE')),
 nr_tentativas INTEGER NOT NULL DEFAULT 0 CHECK(nr_tentativas>=0), dt_criacao TIMESTAMPTZ NOT NULL, dt_alteracao TIMESTAMPTZ NOT NULL,
 dt_lease TIMESTAMPTZ, cd_claim UUID, cd_resultado VARCHAR(64),
 dt_proxima_tentativa TIMESTAMPTZ NOT NULL DEFAULT '-infinity',
 CHECK(cd_emissor='https://securetoken.google.com/'||cd_projeto),
 FOREIGN KEY(cd_projeto,cd_emissor) REFERENCES security.provedor_identidade(cd_projeto,cd_emissor),
 UNIQUE(id_usuario,nr_versao,cd_tipo));
CREATE INDEX comando_pending ON security.comando_identidade(cd_estado,dt_criacao);
CREATE INDEX identidade_lookup ON security.usuario_identidade(id_usuario,dt_inicio,dt_fim);
CREATE INDEX membership_lookup ON security.usuario_tenant(id_usuario,id_tenant,dt_inicio,dt_fim);
CREATE TRIGGER tenant_no_delete BEFORE DELETE ON security.tenant FOR EACH ROW EXECUTE FUNCTION security.prevent_history_delete();
CREATE TRIGGER provider_no_delete BEFORE DELETE ON security.provedor_identidade FOR EACH ROW EXECUTE FUNCTION security.prevent_history_delete();
CREATE TRIGGER user_no_delete BEFORE DELETE ON security.usuario FOR EACH ROW EXECUTE FUNCTION security.prevent_history_delete();
CREATE TRIGGER identity_no_delete BEFORE DELETE ON security.usuario_identidade FOR EACH ROW EXECUTE FUNCTION security.prevent_history_delete();
CREATE TRIGGER membership_no_delete BEFORE DELETE ON security.usuario_tenant FOR EACH ROW EXECUTE FUNCTION security.prevent_history_delete();
CREATE TRIGGER role_no_delete BEFORE DELETE ON security.role_seguranca FOR EACH ROW EXECUTE FUNCTION security.prevent_history_delete();
CREATE TRIGGER profile_no_delete BEFORE DELETE ON security.perfil FOR EACH ROW EXECUTE FUNCTION security.prevent_history_delete();
CREATE TRIGGER group_no_delete BEFORE DELETE ON security.grupo FOR EACH ROW EXECUTE FUNCTION security.prevent_history_delete();
CREATE TRIGGER profile_role_no_delete BEFORE DELETE ON security.perfil_role FOR EACH ROW EXECUTE FUNCTION security.prevent_history_delete();
CREATE TRIGGER member_profile_no_delete BEFORE DELETE ON security.usuario_tenant_perfil FOR EACH ROW EXECUTE FUNCTION security.prevent_history_delete();
CREATE TRIGGER member_role_no_delete BEFORE DELETE ON security.usuario_tenant_role FOR EACH ROW EXECUTE FUNCTION security.prevent_history_delete();
CREATE TRIGGER global_role_no_delete BEFORE DELETE ON security.usuario_role_global FOR EACH ROW EXECUTE FUNCTION security.prevent_history_delete();
CREATE TRIGGER group_member_no_delete BEFORE DELETE ON security.grupo_usuario FOR EACH ROW EXECUTE FUNCTION security.prevent_history_delete();
CREATE TRIGGER group_profile_no_delete BEFORE DELETE ON security.grupo_perfil FOR EACH ROW EXECUTE FUNCTION security.prevent_history_delete();
CREATE TRIGGER group_role_no_delete BEFORE DELETE ON security.grupo_role FOR EACH ROW EXECUTE FUNCTION security.prevent_history_delete();
