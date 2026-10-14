import { useEffect, useRef, type ReactNode } from 'react';

export function DetailDrawer({ open, label, onClose, children, className = '' }: {
  open: boolean; label: string; onClose(): void; children: ReactNode; className?: string;
}) {
  const drawer = useRef<HTMLElement>(null);
  const returnFocus = useRef<HTMLElement | null>(null);
  useEffect(() => {
    if (!open) {
      returnFocus.current?.focus();
      returnFocus.current = null;
      return;
    }
    returnFocus.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { event.preventDefault(); onClose(); }
      if (event.key !== 'Tab' || !drawer.current) return;
      const focusable = [...drawer.current.querySelectorAll<HTMLElement>('a[href],button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),summary,[tabindex]:not([tabindex="-1"])')];
      if (!focusable.length) { event.preventDefault(); drawer.current.focus(); return; }
      const first = focusable[0]; const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    };
    window.addEventListener('keydown', closeOnEscape);
    requestAnimationFrame(() => (drawer.current?.querySelector<HTMLElement>('[data-autofocus]') ?? drawer.current)?.focus());
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener('keydown', closeOnEscape);
    };
  }, [onClose, open]);
  if (!open) return null;
  return <div className="drawer-scrim" data-testid="drawer-scrim">
    <button className="drawer-backdrop" aria-label="상세 패널 닫기" tabIndex={-1} onClick={onClose} />
    <section className={`detail-drawer ${className}`} role="dialog" aria-modal="true" aria-label={label} tabIndex={-1} ref={drawer}>
      {children}
    </section>
  </div>;
}
