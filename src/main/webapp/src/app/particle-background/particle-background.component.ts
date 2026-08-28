import { Component, OnInit } from '@angular/core';

interface Particle {
  x: number;
  y: number;
  size: number;
  opacity: number;
  duration: number;
  delay: number;
  driftX: number;
  driftY: number;
  color: string;
}

@Component({
  selector: 'app-particle-background',
  templateUrl: './particle-background.component.html',
  styleUrl: './particle-background.component.scss',
  standalone: false
})
export class ParticleBackgroundComponent implements OnInit {
  particles: Particle[] = [];
  particleCount = 80;

  offsetX = 0;
  offsetY = 0;

  ngOnInit() {
    this.particles = Array.from({ length: this.particleCount }, () =>
      this.createParticle()
    );
  }

  private createParticle(): Particle {
    return {
      x: this.random(0, 100),
      y: this.random(0, 100),
      size: this.random(2, 10),
      opacity: this.random(0.4, 0.8),
      duration: this.random(12, 28),
      delay: this.random(0, 0),
      driftX: this.random(-120, 120),
      driftY: this.random(-160, 160),
      color: this.randomGlowColor()
    };
  }

  // The particles mirror the design tokens in src/styles/_tokens.scss. They are
  // repeated here because the stylesheet composes them as rgba(<colour>, <alpha>)
  // and so needs the raw channels, which a hex custom property cannot supply.
  // Mostly accent tones, with the two neutrals for depth.
  private randomGlowColor(): string {
    const colors = [
      '253,126,20', // $sd-primary
      '255,164,92', // $sd-primary-text
      '240,169,46', // $sd-warning
      '223,227,232', // $sd-text
      '153,161,172', // $sd-text-muted
    ];

    return colors[Math.floor(Math.random() * colors.length)];
  }

  private random(min: number, max: number): number {
    return +(Math.random() * (max - min) + min).toFixed(2);
  }
}
