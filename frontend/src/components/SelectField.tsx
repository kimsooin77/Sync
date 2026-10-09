import { useEffect, useId, useRef, useState } from 'react';

export type SelectOption<T extends string> = { value: T; label: string };
type Props<T extends string> = {
  label: string;
  value: T;
  options: SelectOption<T>[];
  onChange(value: T): void;
  disabled?: boolean;
  className?: string;
};

export function SelectField<T extends string>({ label, value, options, onChange, disabled = false, className = '' }: Props<T>) {
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(Math.max(0, options.findIndex((option) => option.value === value)));
  const [dropUp, setDropUp] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const listId = useId();
  const selected = options.find((option) => option.value === value) ?? options[0];

  useEffect(() => {
    if (!open) return;
    const dismiss = (event: PointerEvent) => { if (!root.current?.contains(event.target as Node)) setOpen(false); };
    window.addEventListener('pointerdown', dismiss);
    return () => window.removeEventListener('pointerdown', dismiss);
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const bounds = root.current?.getBoundingClientRect();
    if (!bounds) return;
    const availableBelow = window.innerHeight - bounds.bottom;
    const expectedHeight = Math.min(300, options.length * 40);
    setDropUp(availableBelow < expectedHeight && bounds.top > availableBelow);
  }, [open, options.length]);

  function choose(index: number) {
    const option = options[index];
    if (!option || disabled) return;
    onChange(option.value);
    setActive(index);
    setOpen(false);
    trigger.current?.focus();
  }

  function handleKeyDown(event: React.KeyboardEvent<HTMLButtonElement>) {
    if (disabled) return;
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      if (!open) { setActive(Math.max(0, options.findIndex((option) => option.value === value))); setOpen(true); }
      else setActive((index) => (index + (event.key === 'ArrowDown' ? 1 : options.length - 1)) % options.length);
    } else if (event.key === 'Home' && open) { event.preventDefault(); setActive(0); }
    else if (event.key === 'End' && open) { event.preventDefault(); setActive(options.length - 1); }
    else if ((event.key === 'Enter' || event.key === ' ') && open) { event.preventDefault(); choose(active); }
    else if (event.key === 'Escape' && open) { event.preventDefault(); setOpen(false); }
    else if (event.key === 'Tab') setOpen(false);
  }

  useEffect(() => {
    if (!open) return;
    root.current?.querySelector<HTMLElement>(`[data-option-index="${active}"]`)?.scrollIntoView?.({ block: 'nearest' });
  }, [active, open]);

  return <div className={`select-control ${className}`} ref={root}>
    <button ref={trigger} type="button" role="combobox" aria-label={label} aria-expanded={open} aria-controls={listId}
      aria-haspopup="listbox" aria-activedescendant={open ? `${listId}-option-${active}` : undefined}
      disabled={disabled} className="select-trigger" onClick={() => setOpen((current) => !current)} onKeyDown={handleKeyDown}>
      <span>{selected?.label ?? '선택'}</span><svg aria-hidden="true" viewBox="0 0 16 16" className={open ? 'chevron open' : 'chevron'}><path d="m3.5 6 4.5 4 4.5-4" /></svg>
    </button>
    {open && <ul className={dropUp ? 'select-popup drop-up' : 'select-popup'} id={listId} role="listbox" aria-label={label}>
      {options.map((option, index) => <li key={option.value} role="presentation"><button type="button" role="option" id={`${listId}-option-${index}`}
        data-option-index={index} aria-selected={option.value === value} className={index === active ? 'select-option active' : 'select-option'}
        onPointerMove={() => setActive(index)} onClick={() => choose(index)}>{option.label}{option.value === value && <span aria-hidden="true">✓</span>}</button></li>)}
    </ul>}
  </div>;
}
