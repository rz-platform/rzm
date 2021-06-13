import { TurboEvent } from './types';

// See https://caniuse.com/?search=Intl%20DateTimeFormat
const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone;

function setTz() {
  const input = <HTMLInputElement>document.getElementById('timezone');
  if (input && timezone) {
    input.value = timezone;
  }
}

document.addEventListener('turbolinks:load', ((turboEvent: TurboEvent) => {
  const url = turboEvent.data.url;
  if (url && (url.indexOf('signin') > -1 || url.indexOf('signup') > -1)) {
    setTz();
  }
}) as EventListener);

setTz();