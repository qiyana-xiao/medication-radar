import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './api.js';
import Layout from './components/Layout.jsx';
import LoginPage from './pages/LoginPage.jsx';
import WelcomePage from './pages/WelcomePage.jsx';
import AnalyzePage from './pages/AnalyzePage.jsx';
import HistoryPage from './pages/HistoryPage.jsx';
import ReportDetailPage from './pages/ReportDetailPage.jsx';
import DashboardPage from './pages/DashboardPage.jsx';
import KnowledgePage from './pages/KnowledgePage.jsx';

function RequireAuth({ children, allow = ['patient', 'doctor', 'admin'] }) {
  const { user } = useAuth();
  const location = useLocation();

  if (!user) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }
  if (!allow.includes(user.role)) {
    return (
      <div className="page">
        <div className="empty-state">
          <h3>权限不足</h3>
          <p>当前身份无权访问此页面。</p>
        </div>
      </div>
    );
  }
  return children;
}

function AdminRedirect({ children }) {
  const { user } = useAuth();
  if (user?.role === 'admin') {
    return <Navigate to="/knowledge" replace />;
  }
  return children;
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<WelcomePage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route element={<Layout />}>
        <Route path="/home" element={<AdminRedirect><AnalyzePage /></AdminRedirect>} />
        <Route path="/history" element={<RequireAuth allow={['patient', 'doctor']}><HistoryPage /></RequireAuth>} />
        <Route path="/report/:id" element={<RequireAuth allow={['patient', 'doctor']}><ReportDetailPage /></RequireAuth>} />
        <Route path="/dashboard" element={<RequireAuth allow={['patient']}><DashboardPage /></RequireAuth>} />
        <Route path="/knowledge" element={<RequireAuth allow={['admin', 'doctor']}><KnowledgePage /></RequireAuth>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}