import { useEffect, useRef, useState } from 'react';
import { Html5Qrcode } from 'html5-qrcode';
import { Camera, AlertCircle, Pause, Play, Flashlight, FlashlightOff } from 'lucide-react';
import { bipScan } from '../../utils/qr';

/**
 * Scanner QR — mode scan CONTINU.
 *
 * Corrections par rapport à la version précédente :
 *  - **La caméra ne redémarre plus en boucle** : le callback parent est stocké
 *    dans une ref (l'ancien `useEffect([onScanSuccess])` stoppait et relançait
 *    la caméra à chaque re-rendu du parent — soit après chaque scan, puisque
 *    le scan modifiait l'état du composant appelant) ;
 *  - **Déduplication** : les callbacks arrivent ~10 fois/seconde sur la même
 *    étiquette ; le même code est ignoré pendant `delaiDedup` ms ;
 *  - **Pause/reprise** et **torche** (quand l'appareil la supporte) ;
 *  - **Feedback** : biper + vibration au scan accepté.
 *
 * La caméra reste allumée après un scan : c'est le principe du scan continu
 * (on enchaîne les étiquettes sans rien toucher). C'est l'appelant qui décide
 * de fermer ou non via son propre `onScanSuccess`.
 */
const QrScanner = ({ onScanSuccess, delaiDedup = 2000 }) => {
    const [error, setError] = useState('');
    const [starting, setStarting] = useState(true);
    const [paused, setPaused] = useState(false);
    const [torcheActive, setTorcheActive] = useState(false);
    const [torcheDispo, setTorcheDispo] = useState(false);

    const scannerRef = useRef(null);        // instance Html5Qrcode
    const onScanRef = useRef(onScanSuccess); // callback courant SANS redémarrage
    const delaiDedupRef = useRef(delaiDedup);
    const lastScanRef = useRef({ code: null, at: 0 });
    const torcheRef = useRef(false);

    // Toujours le callback à jour, sans effet sur la caméra.
    useEffect(() => {
        onScanRef.current = onScanSuccess;
    }, [onScanSuccess]);

    useEffect(() => {
        delaiDedupRef.current = delaiDedup;
    }, [delaiDedup]);

    useEffect(() => {
        let mounted = true;

        const startScanner = async () => {
            try {
                const qrCodeInstance = new Html5Qrcode('qr-reader');
                scannerRef.current = qrCodeInstance;

                await qrCodeInstance.start(
                    { facingMode: 'environment' },
                    { fps: 10, qrbox: { width: 250, height: 250 } },
                    (decodedText) => {
                        if (!mounted) return;

                        // Dédup : le même code n'est traité qu'une fois par fenêtre.
                        const now = Date.now();
                        const last = lastScanRef.current;
                        if (last.code === decodedText && now - last.at < delaiDedupRef.current) {
                            return;
                        }
                        lastScanRef.current = { code: decodedText, at: now };

                        bipScan();
                        onScanRef.current?.(decodedText);
                    },
                    () => {
                        // Erreur de lecture silencieuse
                    }
                );

                // Torche : proposée uniquement si l'appareil la déclare.
                try {
                    const settings = qrCodeInstance.getRunningTrackSettings?.();
                    setTorcheDispo(!!settings?.capabilities?.torch);
                } catch {
                    setTorcheDispo(false);
                }

                if (mounted) setStarting(false);
            } catch (err) {
                console.error('Erreur démarrage caméra', err);
                if (mounted) {
                    setError("Impossible d'accéder à la caméra. Vérifiez les permissions.");
                    setStarting(false);
                }
            }
        };

        startScanner();

        return () => {
            mounted = false;
            if (scannerRef.current) {
                scannerRef.current
                    .stop()
                    .catch(() => {})
                    .finally(() => {
                        scannerRef.current = null;
                    });
            }
        };
        // Démarrage unique : le composant vit tant que son conteneur est monté.
    }, []);

    const togglePause = async () => {
        const instance = scannerRef.current;
        if (!instance) return;
        try {
            if (paused) {
                await instance.pause(false); // reprise
                setPaused(false);
            } else {
                await instance.pause(true);
                setPaused(true);
                setTorcheActive(false);
                torcheRef.current = false;
            }
        } catch (err) {
            console.error('Erreur pause/reprise', err);
        }
    };

    const toggleTorche = async () => {
        const instance = scannerRef.current;
        if (!instance || !torcheDispo) return;
        try {
            const activer = !torcheRef.current;
            await instance.applyVideoConstraints({ advanced: [{ torch: activer }] });
            torcheRef.current = activer;
            setTorcheActive(activer);
        } catch {
            // Torche non supportée en pratique : on retire le bouton.
            setTorcheDispo(false);
        }
    };

    return (
        <div className="space-y-4">
            <div className="relative">
                <div id="qr-reader" className="rounded-lg overflow-hidden bg-black min-h-[300px]" />
                {starting && (
                    <div className="absolute inset-0 bg-black/80 flex flex-col items-center justify-center text-white">
                        <Camera size={32} className="animate-pulse mb-2" />
                        <p className="text-sm">Démarrage de la caméra...</p>
                    </div>
                )}
                {paused && !starting && (
                    <div className="absolute inset-0 bg-black/70 flex flex-col items-center justify-center text-white">
                        <Pause size={32} className="mb-2" />
                        <p className="text-sm">Scan en pause</p>
                    </div>
                )}
            </div>

            {/* Contrôles caméra */}
            <div className="flex items-center justify-center gap-2">
                <button
                    type="button"
                    onClick={togglePause}
                    disabled={starting || !!error}
                    className="btn-neutral btn-md"
                >
                    {paused ? <Play size={16} /> : <Pause size={16} />}
                    {paused ? 'Reprendre' : 'Pause'}
                </button>
                {torcheDispo && (
                    <button
                        type="button"
                        onClick={toggleTorche}
                        disabled={starting || !!error || paused}
                        className="btn-ghost btn-md"
                        aria-pressed={torcheActive}
                    >
                        {torcheActive ? <FlashlightOff size={16} /> : <Flashlight size={16} />}
                        Torche
                    </button>
                )}
            </div>

            {error && (
                <div className="alert-danger alert">
                    <AlertCircle size={16} className="mt-0.5 shrink-0" />
                    <p className="text-sm">{error}</p>
                </div>
            )}

            <p className="text-xs text-ink-muted text-center">
                Placez le QR Code dans le cadre — le scan reste actif pour enchaîner.
            </p>
        </div>
    );
};

export default QrScanner;
