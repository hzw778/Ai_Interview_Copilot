CREATE TABLE IF NOT EXISTS resumes (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  file_hash VARCHAR(64) NOT NULL UNIQUE,
  original_filename VARCHAR(255) NOT NULL,
  file_size BIGINT,
  content_type VARCHAR(255),
  storage_key VARCHAR(500),
  storage_url VARCHAR(1000),
  resume_text LONGTEXT,
  uploaded_at DATETIME NOT NULL,
  last_accessed_at DATETIME,
  access_count INT DEFAULT 0,
  analyze_status VARCHAR(20),
  analyze_error VARCHAR(500),
  INDEX idx_resume_hash (file_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS resume_analyses (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  resume_id BIGINT NOT NULL,
  overall_score INT,
  content_score INT,
  structure_score INT,
  skill_match_score INT,
  expression_score INT,
  project_score INT,
  summary LONGTEXT,
  strengths_json LONGTEXT,
  suggestions_json LONGTEXT,
  analyzed_at DATETIME NOT NULL,
  CONSTRAINT fk_resume_analysis_resume
    FOREIGN KEY (resume_id) REFERENCES resumes (id) ON DELETE CASCADE,
  INDEX idx_resume_analyses_resume_analyzed (resume_id, analyzed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS interview_sessions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id VARCHAR(36) NOT NULL UNIQUE,
  skill_id VARCHAR(64),
  difficulty VARCHAR(16),
  resume_id BIGINT,
  total_questions INT,
  current_question_index INT DEFAULT 0,
  status VARCHAR(20),
  questions_json LONGTEXT,
  overall_score INT,
  overall_feedback LONGTEXT,
  strengths_json LONGTEXT,
  improvements_json LONGTEXT,
  reference_answers_json LONGTEXT,
  created_at DATETIME NOT NULL,
  completed_at DATETIME,
  evaluate_status VARCHAR(20),
  evaluate_error VARCHAR(500),
  llm_provider VARCHAR(50),
  CONSTRAINT fk_interview_session_resume
    FOREIGN KEY (resume_id) REFERENCES resumes (id) ON DELETE SET NULL,
  INDEX idx_interview_session_resume_created (resume_id, created_at),
  INDEX idx_interview_session_resume_status_created (resume_id, status, created_at),
  INDEX idx_interview_session_skill_created (skill_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS interview_answers (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  question_index INT,
  question LONGTEXT,
  category VARCHAR(255),
  user_answer LONGTEXT,
  score INT,
  feedback LONGTEXT,
  reference_answer LONGTEXT,
  key_points_json LONGTEXT,
  answered_at DATETIME NOT NULL,
  CONSTRAINT fk_interview_answer_session
    FOREIGN KEY (session_id) REFERENCES interview_sessions (id) ON DELETE CASCADE,
  UNIQUE KEY uk_interview_answer_session_question (session_id, question_index),
  INDEX idx_interview_answer_session_question (session_id, question_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS knowledge_bases (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  file_hash VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(255) NOT NULL,
  category VARCHAR(100),
  original_filename VARCHAR(255) NOT NULL,
  file_size BIGINT,
  content_type VARCHAR(255),
  storage_key VARCHAR(500),
  storage_url VARCHAR(1000),
  uploaded_at DATETIME NOT NULL,
  last_accessed_at DATETIME,
  access_count INT DEFAULT 0,
  question_count INT DEFAULT 0,
  vector_status VARCHAR(20),
  vector_error VARCHAR(500),
  chunk_count INT DEFAULT 0,
  INDEX idx_kb_hash (file_hash),
  INDEX idx_kb_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS knowledge_base_chunks (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  knowledge_base_id BIGINT NOT NULL,
  document_id VARCHAR(128) NOT NULL UNIQUE,
  chunk_index INT NOT NULL,
  content LONGTEXT NOT NULL,
  content_length INT NOT NULL,
  embedding_json LONGTEXT NOT NULL,
  source_name VARCHAR(255) NOT NULL,
  source_filename VARCHAR(255) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  CONSTRAINT fk_kb_chunk_knowledge_base
    FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_bases (id) ON DELETE CASCADE,
  INDEX idx_kb_chunk_kb_index (knowledge_base_id, chunk_index),
  INDEX idx_kb_chunk_document_id (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rag_chat_sessions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL,
  status VARCHAR(20),
  created_at DATETIME NOT NULL,
  updated_at DATETIME,
  message_count INT DEFAULT 0,
  is_pinned TINYINT(1) DEFAULT 0,
  INDEX idx_rag_session_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rag_chat_messages (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  type VARCHAR(20) NOT NULL,
  content LONGTEXT NOT NULL,
  message_order INT NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME,
  completed TINYINT(1) DEFAULT 1,
  CONSTRAINT fk_rag_message_session
    FOREIGN KEY (session_id) REFERENCES rag_chat_sessions (id) ON DELETE CASCADE,
  INDEX idx_rag_message_session (session_id),
  INDEX idx_rag_message_order (session_id, message_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rag_session_knowledge_bases (
  session_id BIGINT NOT NULL,
  knowledge_base_id BIGINT NOT NULL,
  PRIMARY KEY (session_id, knowledge_base_id),
  CONSTRAINT fk_rag_session_kb_session
    FOREIGN KEY (session_id) REFERENCES rag_chat_sessions (id) ON DELETE CASCADE,
  CONSTRAINT fk_rag_session_kb_kb
    FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_bases (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
