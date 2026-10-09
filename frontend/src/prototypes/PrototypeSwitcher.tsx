import { useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';

export type LayoutVariant = 'split' | 'drawer' | 'workspace';
export const layoutVariants: { value: LayoutVariant; label: string }[] = [
  { value: 'split', label: '분할 보기' },
  { value: 'drawer', label: '오른쪽 패널' },
  { value: 'workspace', label: '상세 집중' },
];

export function useLayoutVariant(): [LayoutVariant, (variant: LayoutVariant) => void] {
  const [params, setParams] = useSearchParams();
  const raw = params.get('variant');
  const variant: LayoutVariant = raw === 'split' || raw === 'drawer' || raw === 'workspace' ? raw : 'split';
  const setVariant = (next: LayoutVariant) => {
    const updated = new URLSearchParams(params);
    updated.set('variant', next);
    setParams(updated, { replace: true });
  };
  return [variant, setVariant];
}

export function PrototypeSwitcher({ current, onChange }: { current: LayoutVariant; onChange(variant: LayoutVariant): void }) {
  useEffect(() => {
    if (!import.meta.env.DEV) return;
    const cycle = (event: KeyboardEvent) => {
      if (!['ArrowLeft', 'ArrowRight'].includes(event.key)) return;
      const target = event.target as HTMLElement | null;
      if (target?.matches('input, textarea, select, button, [contenteditable="true"]') || target?.closest('[role="listbox"], [role="dialog"]')) return;
      event.preventDefault();
      const currentIndex = layoutVariants.findIndex((item) => item.value === current);
      const step = event.key === 'ArrowRight' ? 1 : -1;
      onChange(layoutVariants[(currentIndex + step + layoutVariants.length) % layoutVariants.length].value);
    };
    window.addEventListener('keydown', cycle);
    return () => window.removeEventListener('keydown', cycle);
  }, [current, onChange]);

  if (!import.meta.env.DEV) return null;
  const index = layoutVariants.findIndex((item) => item.value === current);
  const label = layoutVariants[index]?.label ?? '분할 보기';
  return <nav className="prototype-switcher" aria-label="화면 구성 미리보기 선택">
    <button type="button" aria-label="이전 화면 구성" onClick={() => onChange(layoutVariants[(index + layoutVariants.length - 1) % layoutVariants.length].value)}>←</button>
    <span><b>미리보기</b> {index + 1}/3 · {label}</span>
    <button type="button" aria-label="다음 화면 구성" onClick={() => onChange(layoutVariants[(index + 1) % layoutVariants.length].value)}>→</button>
  </nav>;
}
