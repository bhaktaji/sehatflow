import { useEffect } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { useAuthStore } from "./store/auth";
import AppLayout from "./components/AppLayout";
import LoginPage from "./pages/LoginPage";
import ReceptionPage from "./pages/ReceptionPage";
import DoctorPage from "./pages/DoctorPage";
import PatientPage from "./pages/PatientPage";
import DisplayPage from "./pages/DisplayPage";
import { Loading } from "./components/States";

export default function App() {
  const bootstrap = useAuthStore((state) => state.bootstrap);

  // Restore an existing staff session on first load (cookie-based).
  useEffect(() => {
    void bootstrap();
  }, [bootstrap]);

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route index element={<ReceptionPage />} />
        <Route path="/doctor" element={<DoctorPage />} />
      </Route>
      {/* Public pages: no login, no PII */}
      <Route path="/p/:secret" element={<PatientPage />} />
      <Route path="/display/:secret" element={<DisplayPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

function RequireAuth({ children }: { children: React.ReactNode }) {
  const user = useAuthStore((state) => state.user);
  const initialized = useAuthStore((state) => state.initialized);

  if (!initialized) {
    return (
      <main className="login-page">
        <Loading label="Loading…" />
      </main>
    );
  }
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}
