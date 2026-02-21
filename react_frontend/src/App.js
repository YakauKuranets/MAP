import React from 'react';
import Chat from './Chat';

// Простейшее приложение, демонстрирующее компонентный подход.
// В реальном проекте сюда можно добавить роуты, другие компоненты и стили.

function App() {
  return (
    <div style={{ padding: '20px', fontFamily: 'sans-serif' }}>
      <h1>Map v12 – чат администратора</h1>
      <Chat />
    </div>
  );
}

export default App;