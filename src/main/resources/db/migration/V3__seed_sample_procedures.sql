-- Seed procedures from design mockups (published, verified)

INSERT INTO procedures (
    id, slug, title_fr, title_ar, title_tn, description_fr, description_ar,
    ministry, category, difficulty, delivery_mode, processing_time, fees,
    source_url, source_reference, last_verified_at, status
) VALUES
(
    'a1000000-0000-4000-8000-000000000001',
    'national-id-card-renewal',
    'Renouvellement de la Carte d''Identité Nationale (CIN)',
    'تجديد بطاقة التعريف الوطنية',
    'تجديد بطاقة التعريف',
    'Renewal process for Tunisian national identity cards due to expiration, damage, or data changes.',
    'إجراء تجديد بطاقة التعريف الوطنية عند انتهاء الصلاحية أو التلف أو تغيير البيانات.',
    'Ministry of Interior',
    'CIVIL_STATUS',
    'MODERATE',
    'Walk-in',
    '10-15 business days',
    '3.000 TND',
    'https://www.interieur.gov.tn',
    'Ministry of Interior — CIN renewal',
    '2026-06-01 00:00:00+00',
    'PUBLISHED'
),
(
    'a1000000-0000-4000-8000-000000000002',
    'extrait-de-naissance',
    'Extrait de Naissance',
    'مضمون ولادة',
    'مضموم ولادة',
    'Official birth certificate extract issued by civil status offices.',
    'شهادة مضمون ولادة رسمية تصدرها مكاتب الحالة المدنية.',
    'Ministry of Interior',
    'CIVIL_STATUS',
    'EASY',
    'Instantané',
    '5 mins',
    'Gratuit',
    'https://www.services.tn',
    'Civil status — birth extract',
    '2026-06-01 00:00:00+00',
    'PUBLISHED'
),
(
    'a1000000-0000-4000-8000-000000000003',
    'creation-entreprise-suarl',
    'Création d''Entreprise SUARL',
    'تأسيس شركة الشخص الواحد',
    'تأسيس شركة SUARL',
    'Registration of a single-member limited liability company (SUARL) with APII and RNE.',
    'تسجيل شركة ذات مسؤولية محدودة ذات الشخص الواحد لدى الوكالة والسجل الوطني للمؤسسات.',
    'APII / RNE',
    'BUSINESS',
    'MODERATE',
    'Online & Physical',
    '3-5 Days',
    '150 TND',
    'https://www.apii.tn',
    'APII — SUARL creation',
    '2026-06-01 00:00:00+00',
    'PUBLISHED'
),
(
    'a1000000-0000-4000-8000-000000000004',
    'renouvellement-permis',
    'Renouvellement Permis de Conduire',
    'تجديد رخصة السياقة',
    'تجديد رخصة السياقة',
    'Renewal of a Tunisian driving license at ATTT offices.',
    'تجديد رخصة السياقة التونسية لدى وكالة التونسية للتّرفيه السياحي والسّيارات.',
    'ATTT',
    'VEHICLES',
    'EASY',
    'Walk-in',
    '1 Hour',
    '25 TND',
    'https://www.attt.com.tn',
    'ATTT — driving license renewal',
    '2026-06-01 00:00:00+00',
    'PUBLISHED'
),
(
    'a1000000-0000-4000-8000-000000000005',
    'equivalence-diplome',
    'Équivalence de Diplôme',
    'معادلة الشهائد العلمية',
    'معادلة الشهادة',
    'Recognition and equivalence of foreign academic diplomas in Tunisia.',
    'معادلة واعتراف بالشهادات الأجنبية في تونس.',
    'Ministry of Higher Education',
    'EDUCATION',
    'COMPLEX',
    'Multi-step',
    '30-60 Days',
    '10 TND',
    'https://www.mes.tn',
    'MES — diploma equivalence',
    '2026-06-01 00:00:00+00',
    'PUBLISHED'
);

-- CIN documents
INSERT INTO procedure_documents (id, procedure_id, sort_order, title_fr, title_ar, description_fr, description_ar) VALUES
('b1000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000001', 1,
 'Carte d''identité nationale expirante', 'بطاقة التعريف الوطنية منتهية الصلاحية',
 'The physical card must be surrendered upon issuance of the new one.', 'يجب تسليم البطاقة القديمة عند استلام الجديدة.'),
('b1000000-0000-4000-8000-000000000002', 'a1000000-0000-4000-8000-000000000001', 2,
 'Justificatif de domicile', 'شهادة إقامة',
 'Utility bill or rental contract dated within 3 months.', 'فاتورة كهرباء أو ماء أو عقد كراء لا يتجاوز تاريخه 3 أشهر.'),
('b1000000-0000-4000-8000-000000000003', 'a1000000-0000-4000-8000-000000000001', 3,
 'Photographies d''identité (x3)', '3 صور شمسية',
 'Dimensions 3.5cm x 4.5cm, light background.', 'أبعاد 3.5 سم × 4.5 سم، خلفية فاتحة.'),
('b1000000-0000-4000-8000-000000000004', 'a1000000-0000-4000-8000-000000000001', 4,
 'Timbre fiscal', 'وصل خلاص معلوم الطابع الجبائي',
 'Amount: 3.000 TND, available at local Finance Office.', 'قيمة 3 دنانير، متوفر لدى مكاتب الخزينة.');

-- CIN steps
INSERT INTO procedure_steps (id, procedure_id, step_number, title_fr, title_ar, description_fr, description_ar) VALUES
('c1000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000001', 1,
 'Préparation des documents', 'تحضير الوثائق',
 'Gather all required documents. Ensure photographs meet biometric standards.', 'جمع جميع الوثائق المطلوبة والتأكد من مطابقة الصور للمعايير.'),
('c1000000-0000-4000-8000-000000000002', 'a1000000-0000-4000-8000-000000000001', 2,
 'Visite du commissariat de district', 'الذهاب إلى مركز الشرطة',
 'Head to the police station of your official residence. Morning hours recommended.', 'التوجه إلى مركز الشرطة التابع لمحل إقامتك. يُنصح بالصباح.'),
('c1000000-0000-4000-8000-000000000003', 'a1000000-0000-4000-8000-000000000001', 3,
 'Dépôt et biométrie', 'إيداع الملف والبصمة',
 'Submit your file. Officer verifies documents and may capture biometrics.', 'تسليم الملف للمأمور الذي يتحقق من الوثائق وقد يأخذ البصمة.'),
('c1000000-0000-4000-8000-000000000004', 'a1000000-0000-4000-8000-000000000001', 4,
 'Retrait', 'الاستلام',
 'Present receipt and old CIN to collect the new card (10-15 business days).', 'الحضور بوصل الاستلام والبطاقة القديمة لاستلام الجديدة.');

-- CIN office
INSERT INTO office_locations (id, procedure_id, name, address, city, governorate, hours_fr, hours_ar) VALUES
('d1000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000001',
 'District de Police', 'Rue Habib Bourguiba, Tunis 1000', 'Tunis', 'Tunis',
 'Mon-Fri: 08:30 – 16:30, Sat: 09:00 – 12:00',
 'الإثنين-الجمعة: 08:30 – 16:30، السبت: 09:00 – 12:00');

-- Related procedures for CIN
INSERT INTO procedure_relations (procedure_id, related_procedure_id) VALUES
('a1000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000002');
