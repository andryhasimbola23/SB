import { useContext } from 'react';
import { AuthContext } from '../context/AuthContext';

export const useAuth = () => {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error('useAuth doit être utilisé dans un AuthProvider');
    }

    // Helper : est-ce admin ?
    const isAdmin = () => context.user?.role === 'ADMIN';

    // Helper : est-ce employé ?
    const isEmploye = () => context.user?.role === 'EMPLOYE';

    return {
        ...context,
        isAdmin,
        isEmploye,
    };
};