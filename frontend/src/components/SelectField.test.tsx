import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { SelectField } from './SelectField';

describe('accessible select field', () => {
  it('opens with the keyboard and selects options with arrows and Enter', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<SelectField label="응답 방식" value="NORMAL" options={[
      { value: 'NORMAL', label: '정상 응답' }, { value: 'HTTP_500', label: '서버 오류' }, { value: 'TIMEOUT', label: '응답 시간 초과' },
    ]} onChange={onChange} />);
    const trigger = screen.getByRole('combobox', { name: '응답 방식' });
    await user.click(trigger);
    expect(trigger).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('listbox', { name: '응답 방식' })).toBeInTheDocument();
    await user.keyboard('{END}{ENTER}');
    expect(onChange).toHaveBeenCalledWith('TIMEOUT');
    expect(trigger).toHaveAttribute('aria-expanded', 'false');
  });

  it('supports Home, Escape, disabled state, and an accessible expanded chevron', async () => {
    const user = userEvent.setup();
    render(<SelectField label="재직 상태" value="ACTIVE" options={[
      { value: '', label: '모든 재직 상태' }, { value: 'ACTIVE', label: '재직 중' }, { value: 'TERMINATED', label: '퇴사' },
    ]} onChange={vi.fn()} />);
    const trigger = screen.getByRole('combobox', { name: '재직 상태' });
    await user.click(trigger);
    await user.keyboard('{HOME}');
    expect(screen.getByRole('option', { name: /모든 재직 상태/ })).toHaveAttribute('id', trigger.getAttribute('aria-activedescendant'));
    await user.keyboard('{Escape}');
    expect(trigger).toHaveAttribute('aria-expanded', 'false');
    expect(trigger.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
  });
});
