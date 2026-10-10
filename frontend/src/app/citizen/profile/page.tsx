'use client';

import { useEffect } from 'react';
import Link from 'next/link';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Header } from '@/components/layout';
import { useRequireAuth } from '@/context/AuthContext';
import { updateProfile } from '@/lib/auth';
import { getStoredToken } from '@/lib/auth';
import { toast } from '@/hooks/useToast';

const profileSchema = z.object({
  firstName: z
    .string()
    .trim()
    .min(1, 'Förnamn krävs')
    .max(100, 'Max 100 tecken'),
  lastName: z
    .string()
    .trim()
    .min(1, 'Efternamn krävs')
    .max(100, 'Max 100 tecken'),
  phone: z
    .string()
    .trim()
    .max(40, 'Max 40 tecken')
    .regex(/^[+0-9 \-()]*$/, 'Endast siffror, mellanslag, + och -')
    .optional()
    .or(z.literal('')),
});

type ProfileFormValues = z.infer<typeof profileSchema>;

export default function CitizenProfilePage() {
  const auth = useRequireAuth();
  const { user, updateUser, isLoading } = auth;

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: {
      firstName: '',
      lastName: '',
      phone: '',
    },
  });

  useEffect(() => {
    if (user) {
      reset({
        firstName: user.firstName ?? '',
        lastName: user.lastName ?? '',
        phone: user.phone ?? '',
      });
    }
  }, [user, reset]);

  const onSubmit = handleSubmit(async (values) => {
    const token = getStoredToken();
    if (!token) {
      toast.error('Inte inloggad', 'Du måste vara inloggad för att ändra profilen.');
      return;
    }
    try {
      const updated = await updateProfile(token, {
        firstName: values.firstName,
        lastName: values.lastName,
        phone: values.phone?.trim() ? values.phone.trim() : null,
      });
      updateUser(updated);
      reset({
        firstName: updated.firstName ?? '',
        lastName: updated.lastName ?? '',
        phone: updated.phone ?? '',
      });
      toast.success('Profil uppdaterad', 'Dina uppgifter har sparats.');
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Okänt fel';
      toast.error('Kunde inte spara', message);
    }
  });

  if (isLoading || !user) {
    return (
      <>
        <Header />
        <main className="container mx-auto px-4 py-10 max-w-2xl">
          <p className="text-gray-600">Laddar…</p>
        </main>
      </>
    );
  }

  return (
    <>
      <Header />
      <main className="container mx-auto px-4 py-10 max-w-2xl">
        <nav aria-label="Brödsmulor" className="text-sm text-gray-500 mb-4">
          <Link href="/citizen/cases" className="hover:text-brand-700">
            Mina sidor
          </Link>
          <span className="mx-2">/</span>
          <span aria-current="page" className="text-gray-700">
            Min profil
          </span>
        </nav>

        <h1 className="text-3xl font-bold text-gray-900 mb-2">Min profil</h1>
        <p className="text-gray-600 mb-8">
          Uppdatera dina kontaktuppgifter. E-post kan inte ändras här eftersom
          den är knuten till din inloggning.
        </p>

        <form
          onSubmit={onSubmit}
          className="bg-white border border-gray-200 rounded-lg p-6 space-y-5 shadow-sm"
          noValidate
        >
          <div>
            <label
              htmlFor="email"
              className="block text-sm font-medium text-gray-700 mb-1"
            >
              E-post
            </label>
            <input
              id="email"
              type="email"
              value={user.email}
              readOnly
              aria-readonly="true"
              className="w-full rounded-md border-gray-300 bg-gray-50 text-gray-600 shadow-sm cursor-not-allowed px-3 py-2 border"
            />
            <p className="mt-1 text-xs text-gray-500">
              Hämtas från din inloggning och kan inte ändras här.
            </p>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label
                htmlFor="firstName"
                className="block text-sm font-medium text-gray-700 mb-1"
              >
                Förnamn <span className="text-red-700">*</span>
              </label>
              <input
                id="firstName"
                type="text"
                autoComplete="given-name"
                aria-invalid={errors.firstName ? 'true' : 'false'}
                aria-describedby={errors.firstName ? 'firstName-error' : undefined}
                className="w-full rounded-md border-gray-300 shadow-sm focus:border-brand-500 focus:ring-brand-500 px-3 py-2 border"
                {...register('firstName')}
              />
              {errors.firstName && (
                <p id="firstName-error" className="mt-1 text-sm text-red-700">
                  {errors.firstName.message}
                </p>
              )}
            </div>

            <div>
              <label
                htmlFor="lastName"
                className="block text-sm font-medium text-gray-700 mb-1"
              >
                Efternamn <span className="text-red-700">*</span>
              </label>
              <input
                id="lastName"
                type="text"
                autoComplete="family-name"
                aria-invalid={errors.lastName ? 'true' : 'false'}
                aria-describedby={errors.lastName ? 'lastName-error' : undefined}
                className="w-full rounded-md border-gray-300 shadow-sm focus:border-brand-500 focus:ring-brand-500 px-3 py-2 border"
                {...register('lastName')}
              />
              {errors.lastName && (
                <p id="lastName-error" className="mt-1 text-sm text-red-700">
                  {errors.lastName.message}
                </p>
              )}
            </div>
          </div>

          <div>
            <label
              htmlFor="phone"
              className="block text-sm font-medium text-gray-700 mb-1"
            >
              Telefonnummer
            </label>
            <input
              id="phone"
              type="tel"
              autoComplete="tel"
              inputMode="tel"
              placeholder="+46 70 123 45 67"
              aria-invalid={errors.phone ? 'true' : 'false'}
              aria-describedby={errors.phone ? 'phone-error' : 'phone-help'}
              className="w-full rounded-md border-gray-300 shadow-sm focus:border-brand-500 focus:ring-brand-500 px-3 py-2 border"
              {...register('phone')}
            />
            {errors.phone ? (
              <p id="phone-error" className="mt-1 text-sm text-red-700">
                {errors.phone.message}
              </p>
            ) : (
              <p id="phone-help" className="mt-1 text-xs text-gray-500">
                Används för SMS-notiser om dina ärenden (frivilligt).
              </p>
            )}
          </div>

          <div className="flex items-center justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={() =>
                reset({
                  firstName: user.firstName ?? '',
                  lastName: user.lastName ?? '',
                  phone: user.phone ?? '',
                })
              }
              disabled={!isDirty || isSubmitting}
              className="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              Återställ
            </button>
            <button
              type="submit"
              disabled={!isDirty || isSubmitting}
              className="px-4 py-2 text-sm font-medium text-white bg-brand-600 rounded-md hover:bg-brand-700 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isSubmitting ? 'Sparar…' : 'Spara ändringar'}
            </button>
          </div>
        </form>
      </main>
    </>
  );
}
