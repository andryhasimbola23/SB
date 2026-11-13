import { Navigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';

const ProtectedRoute = ({ children, requiredRole }) => {
    const { user, loading } = useAuth();

    if (loading) {
        return (
            <div className="flex items-center justify-center min-h-screen">
                <div className="text-slate-500">Chargement...</div>
            </div>
        );
    }

    if (!user) {
        return <Navigate to="/login" replace />;
    }

    // Vérification du rôle si requis
    if (requiredRole && user.role !== requiredRole) {
        // Rediriger vers le dashboard si pas autorisé
        return <Navigate to="/dashboard" replace />;
    }

    return children;
};

export default ProtectedRoute;