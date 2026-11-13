import { ButtonLoader } from './Loader';

/**
 * Bouton unifié — utilise les classes `.btn-*` du design system.
 *
 * @param {string} variant - primary | secondary | danger | danger-soft | ghost | neutral
 * @param {string} size    - sm | md | lg
 */
const Button = ({
    children,
    onClick,
    type = 'button',
    variant = 'primary',
    size = 'md',
    loading = false,
    disabled = false,
    icon: Icon,
    iconRight: IconRight,
    fullWidth = false,
    className = '',
    ...props
}) => {
    const variants = {
        primary: 'btn-primary',
        secondary: 'btn-secondary',
        danger: 'btn-danger',
        'danger-soft': 'btn-danger-soft',
        ghost: 'btn-ghost',
        neutral: 'btn-neutral',
    };

    const sizes = {
        sm: 'btn-sm',
        md: 'btn-md',
        lg: 'btn-lg',
    };

    return (
        <button
            type={type}
            onClick={onClick}
            disabled={disabled || loading}
            className={`${variants[variant] || variants.primary} ${sizes[size] || sizes.md} ${
                fullWidth ? 'w-full' : ''
            } ${className}`}
            {...props}
        >
            {loading ? (
                <>
                    <ButtonLoader />
                    <span>Chargement…</span>
                </>
            ) : (
                <>
                    {Icon && <Icon size={size === 'sm' ? 14 : 16} />}
                    {children}
                    {IconRight && <IconRight size={size === 'sm' ? 14 : 16} />}
                </>
            )}
        </button>
    );
};

export default Button;
