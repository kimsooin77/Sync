type BrandSymbolProps = { className?: string };

export function BrandSymbol({ className = '' }: BrandSymbolProps) {
  return <span className={`brand-symbol ${className}`.trim()} aria-hidden="true" data-testid="brand-lettermark">ES</span>;
}
