import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuthStore } from "../store/auth";

export default function LoginPage() {
  const login = useAuthStore((state) => state.login);
  const loading = useAuthStore((state) => state.loading);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    try {
      await login(username.trim(), password);
      navigate("/", { replace: true });
    } catch (requestError) {
      setError(
        requestError instanceof Error ? requestError.message : "Invalid username or password",
      );
    }
  }

  return (
    <main className="login-page">
      <form className="login-card" onSubmit={onSubmit} aria-labelledby="login-title">
        <h1 id="login-title">Clinic Queue</h1>
        <p className="muted">Sign in with your staff account.</p>

        <label htmlFor="username">Username</label>
        <input
          id="username"
          name="username"
          autoComplete="username"
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          required
        />

        <label htmlFor="password">Password</label>
        <input
          id="password"
          name="password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          required
        />

        {error && (
          <p className="field-error" role="alert">
            {error}
          </p>
        )}

        <button type="submit" className="btn btn-primary btn-lg" disabled={loading}>
          {loading ? "Signing in…" : "Sign in"}
        </button>

        <p className="muted small">
          Demo accounts: owner / owner123 · reception / recep123 · doctor / doc123
        </p>
      </form>
    </main>
  );
}
