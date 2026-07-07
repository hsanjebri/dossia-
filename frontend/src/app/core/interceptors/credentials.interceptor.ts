import { HttpInterceptorFn } from '@angular/common/http';
import { environment } from '../../../environments/environment';

/** Send HttpOnly JWT cookie only on auth API calls (avoids CORS issues on public endpoints). */
export const credentialsInterceptor: HttpInterceptorFn = (req, next) => {
  const authPrefix = `${environment.apiUrl}/auth`;
  const chatSessionsPrefix = `${environment.apiUrl}/chat/sessions`;
  if (req.url.startsWith(authPrefix) || req.url.startsWith(chatSessionsPrefix)) {
    return next(req.clone({ withCredentials: true }));
  }
  return next(req);
};
