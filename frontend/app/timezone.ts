// See https://caniuse.com/?search=Intl%20DateTimeFormat
const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone;
const input = <HTMLInputElement>document.getElementById('timezone');

if (input && timezone) {
  input.value = timezone;
}
