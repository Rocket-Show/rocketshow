export class TestPlayResponse {
  durationMillis: number;

  constructor(data?: any) {
    if (!data) {
      return;
    }

    this.durationMillis = data.durationMillis;
  }
}
