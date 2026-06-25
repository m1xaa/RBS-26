import {Component, inject, signal} from '@angular/core';
import { RouterOutlet } from '@angular/router';
import {FormBuilder, FormGroup, FormsModule, ReactiveFormsModule} from '@angular/forms';
import {HttpClient} from '@angular/common/http';

@Component({
  selector: 'app-root',
  imports: [FormsModule, ReactiveFormsModule],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  protected readonly title = signal('frontend');
  userForm: FormGroup;
  apiUrl = "http://localhost:8080/api/weather"
  http = inject(HttpClient);

  constructor(private fb: FormBuilder) {
    this.userForm = this.fb.group({
      yaml: [''],
    });
  }

  onSubmit(): void {
    if (this.userForm.valid) {
      const formData = this.userForm.value;

      this.http.post(this.apiUrl, formData.yaml).subscribe({
        next: (response) => {
          alert(response);
        },
        error: (error) => {
          console.error(error);
        }
      });
    }
  }
}
