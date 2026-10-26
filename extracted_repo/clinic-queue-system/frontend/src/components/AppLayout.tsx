import { NavLink, Outlet } from "react-router-dom";
import { useAuthStore } from "../store/auth";
import { useOnline } from "../hooks/useOnline";

/** Staff shell: navigation, offline banner, logout. */
export default function AppLayout() {
  const user = useAuthStore((state) => state.user);
  const logout = useAuthStore((state) => state.logout);
  const online = useOnline();

  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="brand">Clinic Queue</div>
        <nav aria-label="Main">
          <NavLink to="/" end>
            Reception
          </NavLink>
          <NavLink to="/doctor">Doctor view</NavLink>
        </nav>
        <div className="header-right">
          <span className="who">
            {user?.displayName} ({user?.role})
          </span>
          <button type="button" className="btn btn-ghost" onClick={() => void logout()}>
            Sign out
          </button>
        </div>
      </header>

      {!online && (
        <div role="alert" className="banner banner-offline" data-testid="offline-banner">
          Offline — changes are not saved. Use paper tokens until the connection returns.
        </div>
      )}

      <main className="app-main">
        <Outlet />
      </main>
    </div>
  );
}
