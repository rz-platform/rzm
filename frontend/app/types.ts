export interface TurboEvent extends Event {
  readonly data: {
    url: string;
  };
}
