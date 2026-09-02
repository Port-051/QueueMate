/** REST 실패를 화면이 분기할 수 있게 status와 code를 함께 들고 다닌다. */
export class ApiError extends Error {
  constructor(readonly status: number, readonly code: string, message?: string) {
    super(message ?? code);
    this.name = 'ApiError';
  }
}

export const isApiError = (e: unknown): e is ApiError => e instanceof ApiError;
