export interface RegisterRequest {
  email: string;
  password: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresAt: string;
  user: UserResponse;
}

export interface UserResponse {
  id: string;
  email: string;
  createdAt: string;
}

export interface ApiError {
  code: string;
  message: string;
  fieldErrors?: Record<string, string>;
}
