export type ProcedureCategory =
  | 'CIVIL_STATUS'
  | 'BUSINESS'
  | 'VEHICLES'
  | 'SOCIAL'
  | 'EDUCATION'
  | 'TAX';

export type Difficulty = 'EASY' | 'MODERATE' | 'COMPLEX';

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ProcedureSummary {
  id: string;
  slug: string;
  title: string;
  titleAr: string;
  ministry: string;
  category: ProcedureCategory;
  difficulty: Difficulty;
  deliveryMode: string | null;
  processingTime: string | null;
  fees: string | null;
  lastVerifiedAt: string | null;
}

export interface ProcedureDocument {
  title: string;
  titleAr: string;
  description: string | null;
  descriptionAr: string | null;
  sortOrder: number;
}

export interface ProcedureStep {
  stepNumber: number;
  title: string;
  titleAr: string;
  description: string | null;
  descriptionAr: string | null;
}

export interface OfficeLocation {
  name: string;
  address: string;
  city: string | null;
  governorate: string | null;
  hours: string | null;
  hoursAr: string | null;
  latitude: number | null;
  longitude: number | null;
}

export interface ProcedureDetail extends ProcedureSummary {
  titleTn: string | null;
  description: string | null;
  descriptionAr: string | null;
  sourceUrl: string | null;
  sourceReference: string | null;
  status: string;
  documents: ProcedureDocument[];
  steps: ProcedureStep[];
  offices: OfficeLocation[];
  relatedProcedures: ProcedureSummary[];
}

export interface Category {
  code: ProcedureCategory;
  labelFr: string;
  labelAr: string;
  icon: string;
}

export const CATEGORY_LABELS: Record<ProcedureCategory, string> = {
  CIVIL_STATUS: 'État civil',
  BUSINESS: 'Entreprise',
  VEHICLES: 'Véhicules',
  SOCIAL: 'Social',
  EDUCATION: 'Éducation',
  TAX: 'Fiscalité',
};

export const CATEGORY_ICONS: Record<ProcedureCategory, string> = {
  CIVIL_STATUS: 'person',
  BUSINESS: 'business_center',
  VEHICLES: 'directions_car',
  SOCIAL: 'diversity_3',
  EDUCATION: 'school',
  TAX: 'account_balance',
};
