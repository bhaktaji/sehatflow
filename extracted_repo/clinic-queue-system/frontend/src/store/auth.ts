import { create } from "zustand";
import { api, bootstrapCsrf } from "../api/client";
import type { UserInfo } from "../api/types";

interface AuthState {
  user: UserInfo | null;
  loading: boolean;
  /** True once the initial session probe has completed. */
  initialized: boolean;
  error: string | null;
  bootstrap: () => Promise<void>;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  loading: false,
  initialized: false,
  error: null,

  bootstrap: async () => {
    try {
      await bootstrapCsrf();
      const user = await api.auth.me();
      set({ user, initialized: true });
    } catch {
      set({ user: null, initialized: true });
    }
  },

  login: async (username, password) => {
    set({ loading: true, error: null });
    try {
      await bootstrapCsrf();
      const user = await api.auth.login(username, password);
      set({ user, loading: false, initialized: true });
    } catch (error) {
      const message = error instanceof Error ? error.message : "Login failed";
      set({ error: message, loading: false });
      throw error;
    }
  },

  logout: async () => {
    try {
      await api.auth.logout();
    } finally {
      set({ user: null, initialized: true });
    }
  },
}));
