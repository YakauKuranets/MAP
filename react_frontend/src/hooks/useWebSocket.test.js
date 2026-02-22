import React from 'react';
import { act } from 'react-dom/test-utils';
import { createRoot } from 'react-dom/client';
import useMapStore from '../store/useMapStore';
import useWebSocket from './useWebSocket';

function TestHarness({ wsFactory }) {
  useWebSocket({ url: 'ws://test', wsFactory });
  return React.createElement('div', null, 'ok');
}

describe('useWebSocket', () => {
  let container;
  let root;

  beforeEach(() => {
    useMapStore.getState().reset();
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(() => {
    act(() => {
      root.unmount();
    });
    container.remove();
  });

  test('updates store on pending_created and duty_location_update', () => {
    const socket = {
      onmessage: null,
      close: jest.fn(),
    };
    const wsFactory = jest.fn(() => socket);

    act(() => {
      root.render(React.createElement(TestHarness, { wsFactory }));
    });

    act(() => {
      socket.onmessage({
        data: JSON.stringify({
          event: 'pending_created',
          data: { id: 101, lat: 53.9, lon: 27.56, category: 'Охрана' },
        }),
      });
      socket.onmessage({
        data: JSON.stringify({
          event: 'duty_location_update',
          data: { user_id: 'u-1', lat: 53.95, lon: 27.59, status: 'on_duty' },
        }),
      });
    });

    const state = useMapStore.getState();
    expect(state.incidents).toEqual([
      expect.objectContaining({ id: 101, status: 'pending' }),
    ]);
    expect(state.trackers['u-1']).toEqual(expect.objectContaining({ lat: 53.95, lon: 27.59 }));
    expect(state.statuses['u-1']).toBe('on_duty');

    act(() => {
      root.unmount();
    });
    expect(socket.close).toHaveBeenCalled();
  });
});
