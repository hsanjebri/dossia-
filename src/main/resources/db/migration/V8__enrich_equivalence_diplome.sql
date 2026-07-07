-- Enrich diploma equivalence procedure for RAG chat (engineering degree, foreign diplomas, etc.)

UPDATE procedures
SET
    description_fr = 'L''équivalence de diplôme permet d''assimiler un diplôme obtenu à l''étranger (ou dans un établissement privé tunisien) aux diplômes tunisiens. Elle concerne les diplômes universitaires : licence, ingénieur, master, doctorat, architecte, etc. Après l''obtention d''un diplôme d''ingénieur ou d''un autre diplôme supérieur, vous pouvez demander l''équivalence auprès du Ministère de l''Enseignement Supérieur. La demande se fait en ligne sur la plateforme officielle equivalence.rnu.tn, puis par envoi du dossier papier certifié. Mots-clés : équivalence, diplôme, ingénieur, master, doctorat, diplômé, graduation, titre universitaire, enseignement supérieur.',
    description_ar = 'معادلة الشهادة تتيح معادلة الشهادات الأجنبية أو من مؤسسات خاصة تونسية مع النظام التونسي. تشمل شهادات الإجازة والمهندس والماستر والدكتوراه والمهندس المعماري. بعد التخرج يمكن تقديم طلب المعادلة لدى وزارة التعليم العالي عبر المنصة الرسمية equivalence.rnu.tn.',
    ministry = 'Ministère de l''Enseignement Supérieur, de la Recherche Scientifique et des Technologies de l''Information et de la Communication',
    delivery_mode = 'En ligne + dépôt physique',
    processing_time = '30 à 90 jours ouvrables (variable selon le dossier)',
    fees = 'Frais de dossier selon barème MES — vérifier sur equivalence.rnu.tn',
    source_url = 'https://equivalence.rnu.tn/index_fr.html',
    source_reference = 'MES — Plateforme officielle d''équivalence des diplômes (equivalence.rnu.tn)',
    last_verified_at = NOW(),
    embedding = NULL
WHERE slug = 'equivalence-diplome';

INSERT INTO procedure_documents (id, procedure_id, sort_order, title_fr, title_ar, description_fr, description_ar) VALUES
('b1000000-0000-4000-8000-000000000050', 'a1000000-0000-4000-8000-000000000005', 1,
 'Formulaire de demande d''équivalence (en ligne)', 'استمارة طلب المعادلة (عبر الإنترنت)',
 'Créer un compte et remplir le formulaire sur https://equivalence.rnu.tn', 'إنشاء حساب وملء الاستمارة على المنصة الرسمية'),
('b1000000-0000-4000-8000-000000000051', 'a1000000-0000-4000-8000-000000000005', 2,
 'Copies certifiées conformes de chaque diplôme (à partir du baccalauréat)', 'نسخ مطابقة للأصل من كل شهادة (ابتداء من الباكالوريا)',
 'Certifiées par les autorités tunisiennes en Tunisie et à l''étranger', 'مصادق عليها من السلطات التونسية في تونس وبالخارج'),
('b1000000-0000-4000-8000-000000000052', 'a1000000-0000-4000-8000-000000000005', 3,
 'Relevés de notes de chaque année d''études', 'كشوف نقاط كل سنة دراسية',
 'Copies certifiées conformes : crédits, UV, résultats d''examens', 'نسخ مطابقة للأصل لكل السنوات'),
('b1000000-0000-4000-8000-000000000053', 'a1000000-0000-4000-8000-000000000005', 4,
 'Mémoire / projet de fin d''études / rapport de stage (ingénieur, master, doctorat)', 'مذكرة / مشروع نهاية الدراسة / تقرير تربص (مهندس، ماستر، دكتوراه)',
 'Obligatoire pour les diplômes universitaires dont le diplôme d''ingénieur', 'إلزامي للشهادات الجامعية بما فيها شهادة المهندس'),
('b1000000-0000-4000-8000-000000000054', 'a1000000-0000-4000-8000-000000000005', 5,
 'Pièce d''identité (CIN ou passeport)', 'وثيقة تعريف (بطاقة تعريف أو جواز سفر)',
 NULL, NULL),
('b1000000-0000-4000-8000-000000000055', 'a1000000-0000-4000-8000-000000000005', 6,
 'Mandat légal (si dépôt par un tiers)', 'تفويض قانوني (في حالة الإيداع من طرف ثالث)',
 NULL, NULL);

INSERT INTO procedure_steps (id, procedure_id, step_number, title_fr, title_ar, description_fr, description_ar) VALUES
('c1000000-0000-4000-8000-000000000050', 'a1000000-0000-4000-8000-000000000005', 1,
 'Dépôt en ligne sur equivalence.rnu.tn', 'الإيداع عبر الإنترنت على equivalence.rnu.tn',
 'Créez un compte sur la plateforme officielle et soumettez votre demande avec les scans des documents.', 'أنشئ حساباً على المنصة الرسمية وقدّم طلبك مع المستندات الممسوحة.'),
('c1000000-0000-4000-8000-000000000051', 'a1000000-0000-4000-8000-000000000005', 2,
 'Préparer le dossier papier certifié', 'تحضير الملف الورقي المصادق عليه',
 'Rassemblez les copies certifiées conformes de tous les diplômes et relevés, plus le PFE/mémoire pour un diplôme d''ingénieur.', 'اجمع النسخ المصادق عليها لكل الشهادات والكشوف، مع مشروع نهاية الدراسة لشهادة المهندس.'),
('c1000000-0000-4000-8000-000000000052', 'a1000000-0000-4000-8000-000000000005', 3,
 'Dépôt physique du dossier', 'الإيداع الورقي للملف',
 'Déposez le dossier au bureau d''ordre central du MES à Tunis, ou auprès d''un consulat tunisien si vous êtes à l''étranger.', 'أودع الملف بمكتب الضبط المركزي بالوزارة أو بالقنصلية إن كنت بالخارج.'),
('c1000000-0000-4000-8000-000000000053', 'a1000000-0000-4000-8000-000000000005', 4,
 'Suivi et décision', 'المتابعة والقرار',
 'La Direction des équivalences étudie le dossier. Le délai varie selon la complexité. Vérifiez l''état sur la plateforme.', 'تدرس إدارة المعادلات الملف. المدة تختلف حسب تعقيد الملف.');

INSERT INTO office_locations (id, procedure_id, name, address, city, governorate, hours_fr, hours_ar, latitude, longitude) VALUES
('d1000000-0000-4000-8000-000000000005', 'a1000000-0000-4000-8000-000000000005',
 'Direction des équivalences — MES', '5 Rue de l''Inde, Bab Bhar, Tunis 1002', 'Tunis', 'Tunis',
 'Lun–Ven : 08:00 – 16:30', 'الإثنين–الجمعة : 08:00 – 16:30', 36.7989, 10.1756);
