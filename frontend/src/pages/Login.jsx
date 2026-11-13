import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Moon, Sun, Eye, EyeOff, Lock, User, LogIn } from 'lucide-react';
import { useAuth } from '../hooks/useAuth';
import { useTheme } from '../hooks/useTheme';
import logo from '../assets/Logo_auximad_simple.png';

/**
 * Écran de connexion.
 * Utilise les tokens du design system (fond partagé `.page-surface`, `.input`, `.btn-*`).
 * Responsive : carte pleine largeur sous `sm`, centrée `max-w-md` au-dessus.
 */
const Login = () => {
    const [nomUtilisateur, setNomUtilisateur] = useState('');
    const [motDePasse, setMotDePasse] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    const { login } = useAuth();
    const { isDark, toggleTheme } = useTheme();
    const navigate = useNavigate();

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        setLoading(true);

        try {
            await login(nomUtilisateur, motDePasse);
            navigate('/dashboard');
        } catch (err) {
            setError("Nom d’utilisateur ou mot de passe incorrect.");
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="page-surface page-surface-dots relative min-h-screen flex items-center justify-center p-4 sm:p-6">
            {/* Bascule de thème */}
            <button
                onClick={toggleTheme}
                className="absolute top-4 right-4 btn-ghost btn-icon bg-surface/80 backdrop-blur shadow-soft z-10"
                aria-label={isDark ? 'Mode clair' : 'Mode sombre'}
                title={isDark ? 'Mode clair' : 'Mode sombre'}
            >
                {isDark ? (
                    <Sun size={20} className="text-amber-500" />
                ) : (
                    <Moon size={20} />
                )}
            </button>

            <div className="relative z-[1] w-full max-w-md">
                <div className="card overflow-hidden shadow-panel">
                    {/* Bandeau de marque */}
                    <div className="h-1.5 w-full flex shrink-0">
                        <span className="flex-1 bg-brand-500" />
                        <span className="flex-1 bg-danger-500" />
                    </div>

                    <div className="px-6 sm:px-10 pt-9 pb-8">
                        {/* Logo + titre */}
                        <header className="text-center mb-8">
                            <img
                                src={logo}
                                alt="Logo AUXIMAD"
                                className="w-20 h-20 object-contain mx-auto mb-3.5 transition-transform hover:scale-105"
                                onError={(e) => {
                                    e.currentTarget.style.display = 'none';
                                }}
                            />
                            <h1 className="text-[22px] font-bold tracking-[0.12em] text-ink">AUXIMAD</h1>
                            <p className="text-sm text-ink-muted mt-1.5">Gestion des immobilisations</p>
                        </header>

                        <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
                            {error && (
                                <div className="alert-danger alert !py-2.5 !text-xs text-center justify-center" role="alert">
                                    {error}
                                </div>
                            )}

                            <div>
                                <label htmlFor="username" className="field-label">
                                    Nom d’utilisateur
                                </label>
                                <div className="relative">
                                    <User
                                        size={16}
                                        className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-subtle pointer-events-none"
                                    />
                                    <input
                                        id="username"
                                        type="text"
                                        className="input pl-10"
                                        value={nomUtilisateur}
                                        onChange={(e) => setNomUtilisateur(e.target.value)}
                                        placeholder="Votre identifiant"
                                        autoComplete="username"
                                        required
                                    />
                                </div>
                            </div>

                            <div>
                                <label htmlFor="password" className="field-label">
                                    Mot de passe
                                </label>
                                <div className="relative">
                                    <Lock
                                        size={16}
                                        className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-subtle pointer-events-none"
                                    />
                                    <input
                                        id="password"
                                        type={showPassword ? 'text' : 'password'}
                                        className="input pl-10 pr-11"
                                        value={motDePasse}
                                        onChange={(e) => setMotDePasse(e.target.value)}
                                        placeholder="Votre mot de passe"
                                        autoComplete="current-password"
                                        required
                                    />
                                    <button
                                        type="button"
                                        onClick={() => setShowPassword((s) => !s)}
                                        className="absolute right-2 top-1/2 -translate-y-1/2 p-1.5 text-ink-subtle hover:text-ink transition-colors rounded-control"
                                        aria-label={showPassword ? 'Masquer le mot de passe' : 'Afficher le mot de passe'}
                                    >
                                        {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                                    </button>
                                </div>
                            </div>

                            <button type="submit" disabled={loading} className="btn-primary btn-lg w-full mt-1">
                                {loading ? (
                                    <span className="inline-flex items-center gap-2">
                                        <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                                        Connexion en cours…
                                    </span>
                                ) : (
                                    <>
                                        <LogIn size={16} />
                                        Se connecter
                                    </>
                                )}
                            </button>
                        </form>

                        <footer className="mt-8 pt-5 border-t border-line text-center text-xs text-ink-subtle">
                            © {new Date().getFullYear()} AUXIMAD · Tous droits réservés
                        </footer>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default Login;
