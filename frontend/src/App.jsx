import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Layout from './components/layout/Layout';
import GestionAgences from './pages/agences/GestionAgences';
import GestionServices from './pages/services/GestionServices';
import GestionCategories from './pages/categories/GestionCategories';
import GestionFournisseurs from './pages/fournisseurs/GestionFournisseurs';
import GestionUtilisateurs from './pages/utilisateurs/GestionUtilisateurs';
import GestionImmobilisations from './pages/immobilisations/GestionImmobilisations';
import GestionMouvements from './pages/mouvements/GestionMouvements';
import GestionDemandes from './pages/demandes/GestionDemandes';
import GestionInventaires from './pages/inventaires/GestionInventaires';
import DetailCampagne from './pages/inventaires/DetailCampagne';
import Scanner from './pages/scanner/Scanner';
import JournalAudit from './pages/audit/JournalAudit';
import ProtectedRoute from './components/layout/ProtectedRoute';

function App() {
    return (
        <AuthProvider>
            <BrowserRouter>
                <Routes>
                    <Route path="/login" element={<Login />} />

                    <Route
                        element={
                            <ProtectedRoute>
                                <Layout />
                            </ProtectedRoute>
                        }
                    >
                        {/* Accessibles à tous */}
                        <Route path="/dashboard" element={<Dashboard />} />
                        <Route path="/immobilisations" element={<GestionImmobilisations />} />
                        <Route path="/mouvements" element={<GestionMouvements />} />

                        {/* Scanner global : identification d'un bien par son
                            étiquette (scan continu), ouvert aux deux rôles —
                            le backend filtre le périmètre par service. */}
                        <Route path="/scanner" element={<Scanner />} />

                        {/* Inventaire : campagnes rattachées à une agence et non à un
                            service -> exclusif à l'ADMIN (menu masqué ET route gardée,
                            sinon l'URL directe contournerait l'absence de menu). */}
                        <Route
                            path="/inventaires"
                            element={
                                <ProtectedRoute requiredRole="ADMIN">
                                    <GestionInventaires />
                                </ProtectedRoute>
                            }
                        />
                        <Route
                            path="/inventaires/:id"
                            element={
                                <ProtectedRoute requiredRole="ADMIN">
                                    <DetailCampagne />
                                </ProtectedRoute>
                            }
                        />

                        {/* Réservés ADMIN */}
                        <Route
                            path="/journal"
                            element={
                                <ProtectedRoute requiredRole="ADMIN">
                                    <JournalAudit />
                                </ProtectedRoute>
                            }
                        />
                        <Route
                            path="/agences"
                            element={
                                <ProtectedRoute requiredRole="ADMIN">
                                    <GestionAgences />
                                </ProtectedRoute>
                            }
                        />
                        <Route
                            path="/services"
                            element={
                                <ProtectedRoute requiredRole="ADMIN">
                                    <GestionServices />
                                </ProtectedRoute>
                            }
                        />
                        <Route
                            path="/categories"
                            element={
                                <ProtectedRoute requiredRole="ADMIN">
                                    <GestionCategories />
                                </ProtectedRoute>
                            }
                        />
                        <Route
                            path="/fournisseurs"
                            element={
                                <ProtectedRoute requiredRole="ADMIN">
                                    <GestionFournisseurs />
                                </ProtectedRoute>
                            }
                        />
                        <Route
                            path="/utilisateurs"
                            element={
                                <ProtectedRoute requiredRole="ADMIN">
                                    <GestionUtilisateurs />
                                </ProtectedRoute>
                            }
                        />
                        {/* Demande d'immobilisation : ouverte aux deux rôles, une
                            seule page au contenu adapté (dépôt + suivi pour l'employé,
                            traitement pour l'ADMIN — le backend force les rôles). */}
                        <Route path="/demandes" element={<GestionDemandes />} />

                        {/* Redirections */}
                        <Route path="/" element={<Navigate to="/dashboard" replace />} />
                        <Route path="*" element={<Navigate to="/dashboard" replace />} />
                    </Route>
                </Routes>
            </BrowserRouter>
        </AuthProvider>
    );
}

export default App;
