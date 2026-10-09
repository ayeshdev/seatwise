import type { WorkshopStatus } from '@shared/ui/status-badge';

export type { WorkshopStatus };

/** Shapes below follow the "Payload shapes" block in docs/architecture.md section 9. */

export interface Page<T> {
  items: T[];
  /** Zero-based. */
  page: number;
  size: number;
  totalItems: number;
}

export interface StaffRef {
  id: string;
  fullName: string;
}

export interface Location {
  id: string;
  name: string;
}

export interface Workshop {
  id: string;
  code: string;
  title: string;
  description: string | null;
  instructor: string;
  location: Location;
  startsAt: string;
  endsAt: string;
  capacity: number;
  seatsTaken: number;
  seatsLeft: number;
  waitlistCount: number;
  status: WorkshopStatus;
  version: number;
  createdAt: string;
  createdBy: StaffRef;
  updatedAt: string;
  updatedBy: StaffRef;
}

export type WorkshopSummary = Pick<
  Workshop,
  | 'id'
  | 'code'
  | 'title'
  | 'instructor'
  | 'location'
  | 'startsAt'
  | 'endsAt'
  | 'capacity'
  | 'seatsTaken'
  | 'seatsLeft'
  | 'status'
>;

export type WorkshopSearchResult = Page<WorkshopSummary> & {
  searchMode: 'index' | 'fallback';
};

/** Body of `POST /workshops` and `PUT /workshops/{id}`. */
export interface WorkshopRequest {
  code: string;
  title: string;
  description?: string | null;
  instructor: string;
  locationId: string;
  startsAt: string;
  endsAt: string;
  capacity: number;
  /** Required on PUT. */
  version?: number;
}

/** What the list screen asks the search endpoint for. Dates are `YYYY-MM-DD`. */
export interface WorkshopSearchQuery {
  from?: string | null;
  to?: string | null;
  statuses?: readonly WorkshopStatus[];
  locationId?: string | null;
  hasSeats?: boolean;
  q?: string | null;
  page?: number;
  size?: number;
  sort?: string | null;
}
