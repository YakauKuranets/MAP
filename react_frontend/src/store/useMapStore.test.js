import useMapStore from './useMapStore';

describe('useMapStore', () => {
  beforeEach(() => {
    useMapStore.getState().reset();
  });

  test('adds and removes incidents', () => {
    const state = useMapStore.getState();
    state.addIncident({ id: 1, lat: 53.9, lon: 27.56, category: 'security' });
    state.addIncident({ id: 2, lat: 53.91, lon: 27.57, category: 'fire' });

    expect(useMapStore.getState().incidents).toHaveLength(2);

    useMapStore.getState().removeIncident(1);
    expect(useMapStore.getState().incidents).toEqual([
      expect.objectContaining({ id: 2 }),
    ]);
  });
});
