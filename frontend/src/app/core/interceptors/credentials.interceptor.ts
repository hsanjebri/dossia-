import { HttpInterceptorFn } from '@angular/common/http';
import { environment } from '../../../environments/environment';

/** Send HttpOnly JWT cookie on auth, chat, and chat-session API calls. */
export const credentialsInterceptor: HttpInterceptorFn = (req, next) => {
  const api = environment.apiUrl;
  const needsCredentials =
    req.url.startsWith(`${api}/auth`) ||
    req.url.startsWith(`${api}/chat`);
  if (needsCredentials) {
    return next(req.clone({ withCredentials: true }));
  }
  return next(req);
};
