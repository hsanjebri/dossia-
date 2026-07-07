export interface NearbyOffice {
  id: string;
  name: string;
  officeType: string;
  address: string;
  city: string | null;
  governorate: string | null;
  hours: string | null;
  latitude: number;
  longitude: number;
  distanceKm: number;
  procedureSlug: string | null;
  procedureTitle: string | null;
  mapsUrl: string;
  routeUrl: string;
}

export interface UserLocation {
  lat: number;
  lng: number;
}

export interface RouteInfo {
  distanceKm: number;
  durationMin: number;
  coordinates: [number, number][];
}
