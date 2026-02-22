import { create } from '../vendor/zustand';

const useMapStore = create((set) => ({
  incidents: [],
  trackers: {},
  statuses: {},
  addIncident: (incident) =>
    set((state) => ({
      incidents: [...state.incidents.filter((i) => i.id !== incident.id), incident],
    })),
  removeIncident: (incidentId) =>
    set((state) => ({ incidents: state.incidents.filter((i) => i.id !== incidentId) })),
  upsertTrackerPosition: (trackerId, position) =>
    set((state) => ({
      trackers: {
        ...state.trackers,
        [trackerId]: {
          ...(state.trackers[trackerId] || {}),
          ...position,
        },
      },
    })),
  setTrackerStatus: (trackerId, status) =>
    set((state) => ({ statuses: { ...state.statuses, [trackerId]: status } })),
  reset: () => set({ incidents: [], trackers: {}, statuses: {} }),
}));

export default useMapStore;
