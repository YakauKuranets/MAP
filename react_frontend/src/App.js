import React from 'react';
import CommandCenterMap from './components/CommandCenterMap';
import useWebSocket from './hooks/useWebSocket';

function App() {
  useWebSocket();

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 p-4">
      <header className="mb-4">
        <h1 className="text-2xl font-bold">Map v12 · 3D Командный центр</h1>
        <p className="text-slate-300">Deck.gl + Mapbox + Zustand + WebSocket</p>
      </header>
      <CommandCenterMap />
    </div>
  );
}

export default App;
