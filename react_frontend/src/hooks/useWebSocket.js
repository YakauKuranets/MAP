import { useEffect } from 'react';
import useMapStore from '../store/useMapStore';

export default function useWebSocket({
  url = process.env.REACT_APP_REALTIME_WS_URL || 'ws://localhost:8765',
  wsFactory,
} = {}) {
  const addIncident = useMapStore((s) => s.addIncident);
  const upsertTrackerPosition = useMapStore((s) => s.upsertTrackerPosition);
  const setTrackerStatus = useMapStore((s) => s.setTrackerStatus);

  useEffect(() => {
    const socket = wsFactory ? wsFactory(url) : new WebSocket(url);

    socket.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data || '{}');
        const { event: eventName, data } = message;
        if (eventName === 'pending_created' && data) {
          addIncident({
            id: data.id,
            lat: data.lat,
            lon: data.lon,
            category: data.category,
            status: 'pending',
          });
        }
        if (eventName === 'duty_location_update' && data) {
          const trackerId = String(data.device_id || data.user_id || data.id || 'unknown');
          upsertTrackerPosition(trackerId, {
            lat: data.lat,
            lon: data.lon,
            heading: data.heading,
            speed: data.speed,
          });
          setTrackerStatus(trackerId, data.status || 'online');
        }
      } catch (error) {
        // noop on malformed messages
      }
    };

    return () => {
      if (socket && typeof socket.close === 'function') {
        socket.close();
      }
    };
  }, [url, wsFactory, addIncident, upsertTrackerPosition, setTrackerStatus]);
}
