-- 맥도날드 메뉴 시드 데이터

-- 기존 데이터 삭제
DELETE FROM menu_option;
DELETE FROM menu_synonym;
DELETE FROM menu;
DELETE FROM menu_category;

-- 카테고리 추가
INSERT INTO menu_category (id, name, display_name, display_order) VALUES
(1, 'coffee', '커피', 1),
(2, 'beverage', '음료', 2),
(3, 'burger_set', '버거 세트', 3),
(4, 'burger', '버거', 4),
(5, 'side', '사이드', 5),
(6, 'dessert', '디저트', 6);

-- 커피 메뉴 추가
INSERT INTO menu (id, category_id, name, display_name, description, base_price, has_temperature, has_size) VALUES
(1, 1, 'americano', '아메리카노', '깔끔하고 진한 커피의 맛', 1500.00, true, true),
(2, 1, 'cafe_latte', '카페라떼', '부드러운 우유와 에스프레소의 조화', 2000.00, true, true),
(3, 1, 'cappuccino', '카푸치노', '거품이 풍성한 이탈리안 커피', 2200.00, true, true),
(4, 1, 'caramel_macchiato', '캐러멜 마키아토', '달콤한 캐러멜과 에스프레소', 2500.00, true, true);

-- 음료 메뉴 추가
INSERT INTO menu (id, category_id, name, display_name, description, base_price, has_size) VALUES
(5, 2, 'coca_cola', '코카콜라', '시원한 콜라', 1200.00, true),
(6, 2, 'coca_cola_zero', '코카콜라 제로', '제로 칼로리 콜라', 1200.00, true),
(7, 2, 'sprite', '스프라이트', '상쾌한 사이다', 1200.00, true),
(8, 2, 'orange_juice', '오렌지 주스', '신선한 오렌지 주스', 1500.00, true);

-- 버거 세트 메뉴 추가
INSERT INTO menu (id, category_id, name, display_name, description, base_price, has_set_option) VALUES
(9, 3, 'bigmac_set', '빅맥 세트', '빅맥 + 사이드 + 음료', 5900.00, true),
(10, 3, 'quarterpounder_set', '쿼터파운더 세트', '쿼터파운더 + 사이드 + 음료', 6500.00, true),
(11, 3, 'mcspicy_shanghai_set', '맥스파이시 상하이 버거 세트', '맥스파이시 상하이 버거 + 사이드 + 음료', 6200.00, true),
(12, 3, 'bulgogi_set', '불고기 버거 세트', '불고기 버거 + 사이드 + 음료', 5200.00, true);

-- 버거 단품 메뉴 추가
INSERT INTO menu (id, category_id, name, display_name, description, base_price) VALUES
(13, 4, 'bigmac', '빅맥', '맥도날드 대표 버거', 4200.00),
(14, 4, 'quarterpounder', '쿼터파운더', '1/4파운드 순쇠고기 패티', 4800.00),
(15, 4, 'mcspicy_shanghai', '맥스파이시 상하이 버거', '매콤한 치킨 버거', 4500.00),
(16, 4, 'bulgogi_burger', '불고기 버거', '한국식 불고기 버거', 3500.00);

-- 사이드 메뉴 추가
INSERT INTO menu (id, category_id, name, display_name, description, base_price, has_size) VALUES
(17, 5, 'french_fries', '프렌치프라이', '바삭한 감자튀김', 1800.00, true),
(18, 5, 'chicken_nuggets', '치킨 너겟', '바삭한 치킨 너겟', 2200.00, false),
(19, 5, 'apple_pie', '애플파이', '따뜻한 애플파이', 1500.00, false);

-- 디저트 메뉴 추가
INSERT INTO menu (id, category_id, name, display_name, description, base_price) VALUES
(20, 6, 'mcflurry_oreo', '맥플러리 오레오', '오레오가 들어간 아이스크림', 2500.00),
(21, 6, 'soft_serve_cone', '소프트 콘', '부드러운 소프트 아이스크림', 800.00);

-- 메뉴 동의어 추가 (문서에서 언급된 동의어들 포함)
INSERT INTO menu_synonym (menu_id, synonym, priority) VALUES
-- 아메리카노 동의어
(1, '아아', 1),
(1, '따아', 2),
(1, '아메', 3),
(1, '커피', 4),

-- 카페라떼 동의어
(2, '라떼', 1),
(2, '라떼', 2),

-- 콜라 동의어
(5, '콜라', 1),
(5, '코카', 2),

-- 제로콜라 동의어
(6, '제로', 1),
(6, '제로콜라', 2),
(6, '콜라제로', 3),

-- 세트 메뉴 동의어
(9, '빅맥세트', 1),
(11, '상하이세트', 1),
(11, '상하이 세트', 2),
(11, '맥스파이시세트', 3),

-- 감자튀김 동의어
(17, '감튀', 1),
(17, '감자튀김', 2),
(17, '프라이', 3),

-- 치킨너겟 동의어
(18, '너겟', 1),
(18, '치킨', 2);

-- 커피 온도 옵션
INSERT INTO menu_option (menu_id, option_type, option_name, option_value, additional_price, is_default) VALUES
(1, 'TEMPERATURE', '아이스', 'ice', 0.00, true),
(1, 'TEMPERATURE', '핫', 'hot', 0.00, false),
(2, 'TEMPERATURE', '아이스', 'ice', 0.00, true),
(2, 'TEMPERATURE', '핫', 'hot', 0.00, false),
(3, 'TEMPERATURE', '아이스', 'ice', 0.00, false),
(3, 'TEMPERATURE', '핫', 'hot', 0.00, true),
(4, 'TEMPERATURE', '아이스', 'ice', 0.00, true),
(4, 'TEMPERATURE', '핫', 'hot', 0.00, false);

-- 커피 사이즈 옵션
INSERT INTO menu_option (menu_id, option_type, option_name, option_value, additional_price, is_default) VALUES
(1, 'SIZE', 'S', 'S', 0.00, false),
(1, 'SIZE', 'M', 'M', 300.00, true),
(1, 'SIZE', 'L', 'L', 600.00, false),
(2, 'SIZE', 'S', 'S', 0.00, false),
(2, 'SIZE', 'M', 'M', 300.00, true),
(2, 'SIZE', 'L', 'L', 600.00, false),
(3, 'SIZE', 'S', 'S', 0.00, false),
(3, 'SIZE', 'M', 'M', 300.00, true),
(3, 'SIZE', 'L', 'L', 600.00, false),
(4, 'SIZE', 'S', 'S', 0.00, false),
(4, 'SIZE', 'M', 'M', 300.00, true),
(4, 'SIZE', 'L', 'L', 600.00, false);

-- 음료 사이즈 옵션
INSERT INTO menu_option (menu_id, option_type, option_name, option_value, additional_price, is_default) VALUES
(5, 'SIZE', 'M', 'M', 0.00, true),
(5, 'SIZE', 'L', 'L', 300.00, false),
(6, 'SIZE', 'M', 'M', 0.00, true),
(6, 'SIZE', 'L', 'L', 300.00, false),
(7, 'SIZE', 'M', 'M', 0.00, true),
(7, 'SIZE', 'L', 'L', 300.00, false),
(8, 'SIZE', 'M', 'M', 0.00, true),
(8, 'SIZE', 'L', 'L', 300.00, false);

-- 세트 사이드 옵션
INSERT INTO menu_option (menu_id, option_type, option_name, option_value, additional_price, is_default) VALUES
(9, 'SET_SIDE', '프렌치프라이', 'french_fries', 0.00, true),
(9, 'SET_SIDE', '치킨너겟', 'chicken_nuggets', 400.00, false),
(10, 'SET_SIDE', '프렌치프라이', 'french_fries', 0.00, true),
(10, 'SET_SIDE', '치킨너겟', 'chicken_nuggets', 400.00, false),
(11, 'SET_SIDE', '프렌치프라이', 'french_fries', 0.00, true),
(11, 'SET_SIDE', '치킨너겟', 'chicken_nuggets', 400.00, false),
(12, 'SET_SIDE', '프렌치프라이', 'french_fries', 0.00, true),
(12, 'SET_SIDE', '치킨너겟', 'chicken_nuggets', 400.00, false);

-- 세트 음료 옵션
INSERT INTO menu_option (menu_id, option_type, option_name, option_value, additional_price, is_default) VALUES
(9, 'SET_DRINK', '코카콜라', 'coca_cola', 0.00, true),
(9, 'SET_DRINK', '코카콜라 제로', 'coca_cola_zero', 0.00, false),
(9, 'SET_DRINK', '스프라이트', 'sprite', 0.00, false),
(9, 'SET_DRINK', '오렌지 주스', 'orange_juice', 300.00, false),
(10, 'SET_DRINK', '코카콜라', 'coca_cola', 0.00, true),
(10, 'SET_DRINK', '코카콜라 제로', 'coca_cola_zero', 0.00, false),
(10, 'SET_DRINK', '스프라이트', 'sprite', 0.00, false),
(10, 'SET_DRINK', '오렌지 주스', 'orange_juice', 300.00, false),
(11, 'SET_DRINK', '코카콜라', 'coca_cola', 0.00, true),
(11, 'SET_DRINK', '코카콜라 제로', 'coca_cola_zero', 0.00, false),
(11, 'SET_DRINK', '스프라이트', 'sprite', 0.00, false),
(11, 'SET_DRINK', '오렌지 주스', 'orange_juice', 300.00, false),
(12, 'SET_DRINK', '코카콜라', 'coca_cola', 0.00, true),
(12, 'SET_DRINK', '코카콜라 제로', 'coca_cola_zero', 0.00, false),
(12, 'SET_DRINK', '스프라이트', 'sprite', 0.00, false),
(12, 'SET_DRINK', '오렌지 주스', 'orange_juice', 300.00, false);

-- 감자튀김 사이즈 옵션
INSERT INTO menu_option (menu_id, option_type, option_name, option_value, additional_price, is_default) VALUES
(17, 'SIZE', 'M', 'M', 0.00, true),
(17, 'SIZE', 'L', 'L', 500.00, false); 