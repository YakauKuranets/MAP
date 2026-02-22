import React, { useMemo, useState } from 'react';
import DeckGL, { HexagonLayer, IconLayer } from '../vendor/deckgl';
import Map, { NavigationControl } from '../vendor/reactMapGL';
import useMapStore from '../store/useMapStore';

const ICON_ATLAS =
  'data:image/svg+xml;utf8,' +
  encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64"><circle cx="32" cy="32" r="16" fill="%23ef4444"/></svg>');

export default function CommandCenterMap() {
  const incidents = useMapStore((s) => s.incidents);
  const trackers = useMapStore((s) => s.trackers);
  const [viewState, setViewState] = useState({
    longitude: 27.56,
    latitude: 53.9,
    zoom: 10,
    pitch: 45,
    bearing: 0,
  });

  const trackerData = useMemo(
    () =>
      Object.entries(trackers)
        .map(([id, point]) => ({ id, ...point }))
        .filter((p) => Number.isFinite(p.lon) && Number.isFinite(p.lat)),
    [trackers]
  );

  const incidentData = useMemo(
    () => incidents.filter((i) => Number.isFinite(i.lon) && Number.isFinite(i.lat)),
    [incidents]
  );

  const layers = useMemo(
    () => [
      new IconLayer({
        id: 'tracker-layer',
        data: trackerData,
        pickable: true,
        iconAtlas: ICON_ATLAS,
        iconMapping: { marker: { x: 0, y: 0, width: 64, height: 64, mask: true } },
        getIcon: () => 'marker',
        getSize: () => 18,
        getPosition: (d) => [d.lon, d.lat],
      }),
      new HexagonLayer({
        id: 'incident-hex-layer',
        data: incidentData,
        getPosition: (d) => [d.lon, d.lat],
        radius: 140,
        elevationScale: 20,
        extruded: true,
        pickable: true,
      }),
    ],
    [trackerData, incidentData]
  );

  const mapboxToken = process.env.REACT_APP_MAPBOX_TOKEN || '';

  return (
    <div className="h-[75vh] w-full rounded-lg border border-slate-300 overflow-hidden" data-testid="command-center-map">
      <DeckGL
        layers={layers}
        controller
        viewState={viewState}
        onViewStateChange={({ viewState: next }) => setViewState(next)}
      >
        <Map
          mapStyle="mapbox://styles/mapbox/dark-v11"
          mapboxAccessToken={mapboxToken}
          terrain={{ source: 'mapbox-dem', exaggeration: 1.3 }}
        >
          <NavigationControl position="top-right" />
        </Map>
      </DeckGL>
    </div>
  );
}
