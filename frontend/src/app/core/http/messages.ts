import { KnownProblemCode, ProblemError } from './problem';

/**
 * Plain-language, next-step messages for every code the backend can send. Written for front-desk
 * staff: say what happened and what to do, with no codes, statuses or jargon.
 */
export const PROBLEM_MESSAGES: Record<KnownProblemCode, string> = {
  VALIDATION_FAILED: 'Some details need fixing. Check the highlighted fields and try again.',
  UNAUTHENTICATED: 'Your session has ended. Please sign in again.',
  FORBIDDEN: "You don't have permission to do that. Ask an administrator if you think you should.",
  ACCOUNT_INACTIVE: 'Your account has been deactivated. Please speak to an administrator.',
  NOT_FOUND: "We couldn't find that. It may have been removed. Go back and refresh the page.",
  WORKSHOP_FULL: 'Sorry — the last seat was just taken by a colleague.',
  WORKSHOP_NOT_OPEN:
    'This workshop is no longer open for bookings. It may be cancelled, started or finished.',
  DUPLICATE_REGISTRATION:
    'This person is already registered, or on the waitlist, for this workshop.',
  ALREADY_CANCELLED: 'A colleague already cancelled this. Reload to see the latest.',
  CAPACITY_BELOW_TAKEN:
    'There are already more people registered than that. Choose a higher capacity, or cancel some registrations first.',
  STALE_VERSION: 'Someone else changed this while you were editing. Reload to see their changes.',
  EMAIL_IN_USE: 'That email address already belongs to another account. Use a different one.',
  LAST_ADMIN:
    'There must always be at least one active administrator. Make someone else an administrator first.',
  SELF_MODIFICATION:
    "You can't change your own role or deactivate your own account. Ask another administrator.",
  IDENTITY_UNAVAILABLE: 'Sign-in service is busy. Please try again in a moment.',
};

export const NETWORK_MESSAGE =
  "We couldn't reach Seatwise. Check your internet connection and try again.";
export const SERVER_MESSAGE = 'Something went wrong on our side. Please try again in a moment.';
export const GENERIC_MESSAGE = 'Something went wrong. Please try again, or reload the page.';

/** The sentence to show a person for this failure. */
export function messageFor(problem: ProblemError): string {
  if (problem.code === 'NETWORK') {
    return NETWORK_MESSAGE;
  }
  if (problem.code === 'UNKNOWN') {
    return problem.status >= 500 ? SERVER_MESSAGE : GENERIC_MESSAGE;
  }
  return PROBLEM_MESSAGES[problem.code];
}
