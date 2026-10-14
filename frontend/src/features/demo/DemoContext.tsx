import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { request } from '../../api/client';
import type { FailureSimulation, HrScenario, ScenarioResponse } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';

type DemoContextValue = {
  scenario: HrScenario | null;
  simulation: FailureSimulation | null;
  scenarioUnavailable: boolean;
  simulationUnavailable: boolean;
  refresh(): Promise<{ scenarioOk: boolean; simulationOk: boolean }>;
  setScenario(value: HrScenario): void;
  setSimulation(value: FailureSimulation): void;
};

const DemoContext = createContext<DemoContextValue>({
  scenario: null,
  simulation: null,
  scenarioUnavailable: true,
  simulationUnavailable: true,
  refresh: async () => ({ scenarioOk: false, simulationOk: false }),
  setScenario: () => undefined,
  setSimulation: () => undefined,
});

export function DemoProvider({ children }: { children: ReactNode }) {
  const { csrfRejected, expireSession } = useAuth();
  const [scenario, setScenario] = useState<HrScenario | null>(null);
  const [simulation, setSimulation] = useState<FailureSimulation | null>(null);
  const [scenarioUnavailable, setScenarioUnavailable] = useState(true);
  const [simulationUnavailable, setSimulationUnavailable] = useState(true);

  const refresh = useCallback(async () => {
    const results = await Promise.allSettled([
      request<ScenarioResponse>('/mock/hr/scenario', {}, expireSession, csrfRejected),
      request<FailureSimulation>('/mock/groupware/failure-simulation', {}, expireSession, csrfRejected),
    ]);
    const [scenarioResult, simulationResult] = results;
    if (scenarioResult.status === 'fulfilled') {
      setScenario(scenarioResult.value.scenario);
      setScenarioUnavailable(false);
    } else setScenarioUnavailable(true);
    if (simulationResult.status === 'fulfilled') {
      setSimulation(simulationResult.value);
      setSimulationUnavailable(false);
    } else setSimulationUnavailable(true);
    return { scenarioOk: scenarioResult.status === 'fulfilled', simulationOk: simulationResult.status === 'fulfilled' };
  }, [csrfRejected, expireSession]);

  useEffect(() => { void refresh(); }, [refresh]);

  const value = useMemo(() => ({
    scenario, simulation, scenarioUnavailable, simulationUnavailable, refresh,
    setScenario(value: HrScenario) { setScenario(value); setScenarioUnavailable(false); },
    setSimulation(value: FailureSimulation) { setSimulation(value); setSimulationUnavailable(false); },
  }), [refresh, scenario, simulation, scenarioUnavailable, simulationUnavailable]);
  return <DemoContext.Provider value={value}>{children}</DemoContext.Provider>;
}

export function useDemoSettings() { return useContext(DemoContext); }
