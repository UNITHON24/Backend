-- 맥도날드 메뉴 시스템 스키마

-- 메뉴 카테고리 테이블
CREATE TABLE IF NOT EXISTS menu_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    display_order INT DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 메뉴 아이템 테이블
CREATE TABLE IF NOT EXISTS menu (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    description TEXT,
    base_price DECIMAL(10,2) NOT NULL,
    is_active BOOLEAN DEFAULT true,
    has_temperature BOOLEAN DEFAULT false, -- 아이스/핫 선택 가능 여부
    has_size BOOLEAN DEFAULT false, -- 사이즈 선택 가능 여부
    has_set_option BOOLEAN DEFAULT false, -- 세트 옵션 가능 여부
    display_order INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES menu_category(id)
);

-- 메뉴 동의어 테이블
CREATE TABLE IF NOT EXISTS menu_synonym (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    menu_id BIGINT NOT NULL,
    synonym VARCHAR(200) NOT NULL,
    priority INT DEFAULT 1, -- 우선순위 (낮을수록 높은 우선순위)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (menu_id) REFERENCES menu(id),
    UNIQUE KEY unique_synonym (synonym)
);

-- 메뉴 옵션 테이블 (사이즈, 온도, 세트 옵션 등)
CREATE TABLE IF NOT EXISTS menu_option (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    menu_id BIGINT NOT NULL,
    option_type ENUM('SIZE', 'TEMPERATURE', 'SET_SIDE', 'SET_DRINK', 'ADD_ON') NOT NULL,
    option_name VARCHAR(100) NOT NULL,
    option_value VARCHAR(100) NOT NULL,
    additional_price DECIMAL(10,2) DEFAULT 0.00,
    is_default BOOLEAN DEFAULT false,
    is_active BOOLEAN DEFAULT true,
    display_order INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (menu_id) REFERENCES menu(id)
);

-- 인덱스 생성
CREATE INDEX idx_menu_category_id ON menu(category_id);
CREATE INDEX idx_menu_active ON menu(is_active);
CREATE INDEX idx_synonym_menu_id ON menu_synonym(menu_id);
CREATE INDEX idx_option_menu_id ON menu_option(menu_id); 