-- ============================================================================
-- VigieChiro Companion - Schema complet (MCD section 3 du draft architecture).
-- Cible : SQLite. Dates/heures = TEXT ISO-8601, booleens = INTEGER 0/1,
-- UUID = TEXT, structures serialisees = TEXT JSON.
-- ON DELETE CASCADE sur les entites faibles dependantes d'une session.
-- ============================================================================

-- C1 - Utilisateur (mono-utilisateur, une seule ligne en pratique)
CREATE TABLE user (
  local_id     TEXT PRIMARY KEY,
  display_name TEXT
);

-- C2 - Site de suivi (R1 numero de carre ; R5 partielle via unicite par user)
CREATE TABLE monitoring_site (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  square_number TEXT NOT NULL,
  friendly_name TEXT,
  protocol      TEXT NOT NULL,
  comment       TEXT,
  created_at    TEXT NOT NULL,
  user_id       TEXT NOT NULL REFERENCES user(local_id) ON DELETE CASCADE,
  UNIQUE(user_id, square_number)
);

-- C3 - Point d'ecoute (R2 code lettre+chiffre)
CREATE TABLE listening_point (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  code        TEXT NOT NULL,
  gps_lat     REAL,
  gps_lon     REAL,
  description TEXT,
  site_id     INTEGER NOT NULL REFERENCES monitoring_site(id) ON DELETE CASCADE,
  UNIQUE(site_id, code)
);

-- C4 - Enregistreur (identite lue du LogPR)
CREATE TABLE recorder (
  serial_number TEXT PRIMARY KEY,
  model_version TEXT,
  comment       TEXT
);

-- C4bis - Micro (historisation : is_active + decommissioned_at)
CREATE TABLE microphone (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  model_ref         TEXT NOT NULL,
  bandwidth         TEXT,
  sensitivity       TEXT,
  commissioned_at   TEXT,
  decommissioned_at TEXT,
  is_active         INTEGER NOT NULL DEFAULT 1,
  comment           TEXT,
  recorder_id       TEXT NOT NULL REFERENCES recorder(serial_number) ON DELETE CASCADE
);

-- C5 - Passage (ENTITE CENTRALE ; R5 unicite quadruplet)
CREATE TABLE passage (
  id                   INTEGER PRIMARY KEY AUTOINCREMENT,
  passage_number       INTEGER NOT NULL,
  year                 INTEGER NOT NULL,
  recording_date       TEXT NOT NULL,
  start_time           TEXT NOT NULL,
  end_time             TEXT NOT NULL,
  acquisition_params   TEXT,
  workflow_status      TEXT NOT NULL,
  verification_verdict TEXT,
  comment              TEXT,
  weather_data         TEXT,
  deposited_at         TEXT,
  point_id             INTEGER NOT NULL REFERENCES listening_point(id) ON DELETE CASCADE,
  recorder_id          TEXT NOT NULL REFERENCES recorder(serial_number),
  UNIQUE(point_id, year, passage_number)
);

-- C6 - Session d'enregistrement (1:1 passage)
CREATE TABLE recording_session (
  id                    INTEGER PRIMARY KEY AUTOINCREMENT,
  root_path             TEXT NOT NULL,
  originals_total_bytes INTEGER,
  sequences_total_bytes INTEGER,
  passage_id            INTEGER NOT NULL UNIQUE REFERENCES passage(id) ON DELETE CASCADE
);

-- C7 - Enregistrement original
CREATE TABLE original_recording (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  file_name      TEXT NOT NULL,
  file_path      TEXT NOT NULL,
  duration_s     REAL,
  sample_rate_hz INTEGER,
  sha256         TEXT,
  session_id     INTEGER NOT NULL REFERENCES recording_session(id) ON DELETE CASCADE
);

-- C8 - Sequence d'ecoute
CREATE TABLE listening_sequence (
  id                    INTEGER PRIMARY KEY AUTOINCREMENT,
  file_name             TEXT NOT NULL,
  original_recording_id INTEGER NOT NULL REFERENCES original_recording(id) ON DELETE CASCADE,
  source_index          INTEGER,
  source_offset_s       REAL,
  duration_s            REAL,
  file_path             TEXT NOT NULL,
  in_selection          INTEGER NOT NULL DEFAULT 0,
  session_id            INTEGER NOT NULL REFERENCES recording_session(id) ON DELETE CASCADE
);

-- C9 - Journal du capteur (1:1 session)
CREATE TABLE sensor_log (
  id                 INTEGER PRIMARY KEY AUTOINCREMENT,
  file_path          TEXT NOT NULL,
  parsed_events      TEXT,
  detected_anomalies TEXT,
  session_id         INTEGER NOT NULL UNIQUE REFERENCES recording_session(id) ON DELETE CASCADE
);

-- C10 - Releve climatique (0:1 session)
CREATE TABLE climate_log (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  file_path    TEXT NOT NULL,
  measurements TEXT,
  session_id   INTEGER NOT NULL UNIQUE REFERENCES recording_session(id) ON DELETE CASCADE
);

-- C11 - Selection d'ecoute (0:1 passage)
CREATE TABLE listening_selection (
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  selection_method TEXT NOT NULL,
  size             INTEGER NOT NULL,
  passage_id       INTEGER NOT NULL UNIQUE REFERENCES passage(id) ON DELETE CASCADE
);

-- Jonction N..N selection <-> sequence (C11 <-> C8)
CREATE TABLE selection_sequence (
  selection_id INTEGER NOT NULL REFERENCES listening_selection(id) ON DELETE CASCADE,
  sequence_id  INTEGER NOT NULL REFERENCES listening_sequence(id) ON DELETE CASCADE,
  position     INTEGER NOT NULL,
  listened     INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (selection_id, sequence_id)
);

-- C15 - Groupe taxonomique
CREATE TABLE taxonomic_group (
  id    INTEGER PRIMARY KEY AUTOINCREMENT,
  level TEXT NOT NULL,
  name  TEXT NOT NULL
);

-- C14 - Taxon (cle naturelle : code 6 lettres, + pseudo-taxons noise/piaf)
CREATE TABLE taxon (
  code               TEXT PRIMARY KEY,
  latin_name         TEXT,
  vernacular_name_fr TEXT,
  group_id           INTEGER NOT NULL REFERENCES taxonomic_group(id)
);

-- C12 - Resultats d'identification (0:1 passage)
CREATE TABLE identification_results (
  id              INTEGER PRIMARY KEY AUTOINCREMENT,
  file_path       TEXT NOT NULL,
  detected_format TEXT NOT NULL,
  imported_at     TEXT NOT NULL,
  passage_id      INTEGER NOT NULL UNIQUE REFERENCES passage(id) ON DELETE CASCADE
);

-- C13 - Observation (3 FK vers taxon : tadarida requis, other/observer optionnels)
CREATE TABLE observation (
  id                   INTEGER PRIMARY KEY AUTOINCREMENT,
  sequence_id          INTEGER NOT NULL REFERENCES listening_sequence(id) ON DELETE CASCADE,
  start_time_s         REAL,
  end_time_s           REAL,
  median_freq_hz       INTEGER,
  taxon_tadarida       TEXT NOT NULL REFERENCES taxon(code),
  prob_tadarida        REAL,
  taxon_other_tadarida TEXT REFERENCES taxon(code),
  taxon_observer       TEXT REFERENCES taxon(code),
  prob_observer        REAL,
  user_comment         TEXT,
  is_reference         INTEGER NOT NULL DEFAULT 0,
  validation_mode      TEXT,
  results_id           INTEGER NOT NULL REFERENCES identification_results(id) ON DELETE CASCADE
);

-- E5.S3 - Vues sauvegardees
CREATE TABLE saved_view (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  name         TEXT NOT NULL,
  filters_json TEXT NOT NULL
);

-- E0.S8 - Versioning du schema (rempli par MigrationSchema)
CREATE TABLE schema_version (
  version    INTEGER PRIMARY KEY,
  applied_at TEXT NOT NULL
);

-- Index utiles (perf O5)
CREATE INDEX idx_passage_point ON passage(point_id);
CREATE INDEX idx_seq_session   ON listening_sequence(session_id);
CREATE INDEX idx_obs_sequence  ON observation(sequence_id);
CREATE INDEX idx_obs_taxon     ON observation(taxon_tadarida);
