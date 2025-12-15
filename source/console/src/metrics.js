// Import faro instance
import faro from './faro';

// Simple metrics tracking via Faro SDK
let metrics = {
  pageLoads: 0,
  errors: 0
};

if (window.APP_CONFIG?.FARO_ENABLED !== false && faro) {
  const recordPageLoad = () => {
    metrics.pageLoads++;
    // Use Faro API to send custom measurement
    faro.api.pushMeasurement({
      type: 'counter',
      name: 'page_loads_total',
      value: metrics.pageLoads,
      attributes: {
        'service.name': 'iso8583-console'
      }
    });
  };
  
  const recordError = () => {
    metrics.errors++;
    faro.api.pushMeasurement({
      type: 'counter', 
      name: 'errors_total',
      value: metrics.errors,
      attributes: {
        'service.name': 'iso8583-console'
      }
    });
  };

  // Track initial page load
  recordPageLoad();
  
  // Export metrics functions
  window.metrics = {
    recordError,
    recordPageLoad
  };

  console.log('✅ Faro SDK Metrics initialized');
} else {
  // Fallback if Faro is not available
  window.metrics = {
    recordError: () => console.log('Error recorded'),
    recordPageLoad: () => console.log('Page load recorded')
  };
}

export { metrics };