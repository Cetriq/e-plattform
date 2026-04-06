'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getFlowTypes,
  getCategories,
  createFlowType,
  updateFlowType,
  deleteFlowType,
  createCategory,
  updateCategory,
  deleteCategory,
  type FlowType,
  type Category,
  type CreateFlowTypeRequest,
  type CreateCategoryRequest,
} from '@/lib/api/admin';

export default function CategoriesPage() {
  const queryClient = useQueryClient();
  const [editingFlowType, setEditingFlowType] = useState<FlowType | null>(null);
  const [editingCategory, setEditingCategory] = useState<Category | null>(null);
  const [isNewFlowType, setIsNewFlowType] = useState(false);
  const [isNewCategory, setIsNewCategory] = useState(false);
  const [selectedFlowTypeId, setSelectedFlowTypeId] = useState<string | null>(null);

  // Fetch data
  const { data: flowTypes = [], isLoading: flowTypesLoading } = useQuery({
    queryKey: ['flowTypes'],
    queryFn: getFlowTypes,
  });

  const { data: categories = [], isLoading: categoriesLoading } = useQuery({
    queryKey: ['categories'],
    queryFn: getCategories,
  });

  // Mutations
  const createFlowTypeMutation = useMutation({
    mutationFn: createFlowType,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['flowTypes'] });
      setIsNewFlowType(false);
      setEditingFlowType(null);
    },
  });

  const updateFlowTypeMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: CreateFlowTypeRequest }) =>
      updateFlowType(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['flowTypes'] });
      setEditingFlowType(null);
    },
  });

  const deleteFlowTypeMutation = useMutation({
    mutationFn: deleteFlowType,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['flowTypes'] });
    },
  });

  const createCategoryMutation = useMutation({
    mutationFn: createCategory,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
      queryClient.invalidateQueries({ queryKey: ['flowTypes'] });
      setIsNewCategory(false);
      setEditingCategory(null);
    },
  });

  const updateCategoryMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: CreateCategoryRequest }) =>
      updateCategory(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
      queryClient.invalidateQueries({ queryKey: ['flowTypes'] });
      setEditingCategory(null);
    },
  });

  const deleteCategoryMutation = useMutation({
    mutationFn: deleteCategory,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
      queryClient.invalidateQueries({ queryKey: ['flowTypes'] });
    },
  });

  const handleSaveFlowType = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const formData = new FormData(e.currentTarget);
    const data: CreateFlowTypeRequest = {
      name: formData.get('name') as string,
      description: formData.get('description') as string || undefined,
      color: formData.get('color') as string || undefined,
      icon: formData.get('icon') as string || undefined,
      sortOrder: parseInt(formData.get('sortOrder') as string) || 0,
    };

    if (isNewFlowType) {
      createFlowTypeMutation.mutate(data);
    } else if (editingFlowType) {
      updateFlowTypeMutation.mutate({ id: editingFlowType.id, data });
    }
  };

  const handleSaveCategory = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const formData = new FormData(e.currentTarget);
    const data: CreateCategoryRequest = {
      name: formData.get('name') as string,
      description: formData.get('description') as string || undefined,
      sortOrder: parseInt(formData.get('sortOrder') as string) || 0,
      flowTypeId: formData.get('flowTypeId') as string || undefined,
    };

    if (isNewCategory) {
      createCategoryMutation.mutate(data);
    } else if (editingCategory) {
      updateCategoryMutation.mutate({ id: editingCategory.id, data });
    }
  };

  if (flowTypesLoading || categoriesLoading) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-purple-600"></div>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Kategorier</h1>
        <p className="mt-1 text-gray-500">
          Hantera tjänstetyper och kategorier för e-tjänster
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        {/* Flow Types Section */}
        <div className="bg-white rounded-xl shadow-sm border">
          <div className="p-6 border-b flex items-center justify-between">
            <div>
              <h2 className="text-lg font-semibold text-gray-900">Tjänstetyper</h2>
              <p className="text-sm text-gray-500">Huvudkategorier för e-tjänster</p>
            </div>
            <button
              onClick={() => {
                setIsNewFlowType(true);
                setEditingFlowType({
                  id: '',
                  name: '',
                  sortOrder: flowTypes.length,
                  categories: [],
                });
              }}
              className="px-4 py-2 bg-purple-600 text-white rounded-lg text-sm font-medium
                       hover:bg-purple-700 transition-colors flex items-center gap-2"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
              </svg>
              Ny tjänstetyp
            </button>
          </div>

          <div className="divide-y">
            {flowTypes.length === 0 ? (
              <div className="p-8 text-center text-gray-500">
                <svg className="w-12 h-12 mx-auto mb-4 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
                </svg>
                <p>Inga tjänstetyper ännu</p>
              </div>
            ) : (
              flowTypes.map((flowType) => (
                <div
                  key={flowType.id}
                  className={`p-4 hover:bg-gray-50 cursor-pointer transition-colors ${
                    selectedFlowTypeId === flowType.id ? 'bg-purple-50' : ''
                  }`}
                  onClick={() => setSelectedFlowTypeId(flowType.id)}
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div
                        className="w-10 h-10 rounded-lg flex items-center justify-center text-white font-bold"
                        style={{ backgroundColor: flowType.color || '#6b7280' }}
                      >
                        {flowType.name.charAt(0)}
                      </div>
                      <div>
                        <h3 className="font-medium text-gray-900">{flowType.name}</h3>
                        <p className="text-sm text-gray-500">
                          {flowType.categories.length} kategorier
                        </p>
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          setIsNewFlowType(false);
                          setEditingFlowType(flowType);
                        }}
                        className="p-2 text-gray-400 hover:text-purple-600 transition-colors"
                      >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
                        </svg>
                      </button>
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          if (confirm('Vill du ta bort denna tjänstetyp?')) {
                            deleteFlowTypeMutation.mutate(flowType.id);
                          }
                        }}
                        className="p-2 text-gray-400 hover:text-red-600 transition-colors"
                      >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                        </svg>
                      </button>
                    </div>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Categories Section */}
        <div className="bg-white rounded-xl shadow-sm border">
          <div className="p-6 border-b flex items-center justify-between">
            <div>
              <h2 className="text-lg font-semibold text-gray-900">Underkategorier</h2>
              <p className="text-sm text-gray-500">
                {selectedFlowTypeId
                  ? `Kategorier för ${flowTypes.find((ft) => ft.id === selectedFlowTypeId)?.name}`
                  : 'Välj en tjänstetyp för att se kategorier'}
              </p>
            </div>
            <button
              onClick={() => {
                setIsNewCategory(true);
                setEditingCategory({
                  id: '',
                  name: '',
                  sortOrder: categories.length,
                  flowTypeId: selectedFlowTypeId || undefined,
                });
              }}
              className="px-4 py-2 bg-purple-600 text-white rounded-lg text-sm font-medium
                       hover:bg-purple-700 transition-colors flex items-center gap-2"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
              </svg>
              Ny kategori
            </button>
          </div>

          <div className="divide-y">
            {(() => {
              const filteredCategories = selectedFlowTypeId
                ? categories.filter((c) => c.flowTypeId === selectedFlowTypeId)
                : categories;

              if (filteredCategories.length === 0) {
                return (
                  <div className="p-8 text-center text-gray-500">
                    <svg className="w-12 h-12 mx-auto mb-4 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.994 1.994 0 013 12V7a4 4 0 014-4z" />
                    </svg>
                    <p>Inga kategorier ännu</p>
                  </div>
                );
              }

              return filteredCategories.map((category) => (
                <div key={category.id} className="p-4 hover:bg-gray-50 transition-colors">
                  <div className="flex items-center justify-between">
                    <div>
                      <h3 className="font-medium text-gray-900">{category.name}</h3>
                      {category.description && (
                        <p className="text-sm text-gray-500">{category.description}</p>
                      )}
                      {category.flowTypeName && !selectedFlowTypeId && (
                        <span className="text-xs text-purple-600 bg-purple-50 px-2 py-0.5 rounded mt-1 inline-block">
                          {category.flowTypeName}
                        </span>
                      )}
                    </div>
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => {
                          setIsNewCategory(false);
                          setEditingCategory(category);
                        }}
                        className="p-2 text-gray-400 hover:text-purple-600 transition-colors"
                      >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
                        </svg>
                      </button>
                      <button
                        onClick={() => {
                          if (confirm('Vill du ta bort denna kategori?')) {
                            deleteCategoryMutation.mutate(category.id);
                          }
                        }}
                        className="p-2 text-gray-400 hover:text-red-600 transition-colors"
                      >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                        </svg>
                      </button>
                    </div>
                  </div>
                </div>
              ));
            })()}
          </div>
        </div>
      </div>

      {/* Edit Flow Type Modal */}
      {editingFlowType && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full mx-4">
            <form onSubmit={handleSaveFlowType}>
              <div className="p-6 border-b">
                <h3 className="text-lg font-semibold text-gray-900">
                  {isNewFlowType ? 'Ny tjänstetyp' : 'Redigera tjänstetyp'}
                </h3>
              </div>
              <div className="p-6 space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Namn *
                  </label>
                  <input
                    name="name"
                    type="text"
                    defaultValue={editingFlowType.name}
                    required
                    className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-purple-500"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Beskrivning
                  </label>
                  <textarea
                    name="description"
                    defaultValue={editingFlowType.description || ''}
                    rows={2}
                    className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-purple-500"
                  />
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      Färg
                    </label>
                    <input
                      name="color"
                      type="color"
                      defaultValue={editingFlowType.color || '#6b7280'}
                      className="w-full h-10 border rounded-lg cursor-pointer"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      Sortering
                    </label>
                    <input
                      name="sortOrder"
                      type="number"
                      defaultValue={editingFlowType.sortOrder}
                      className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-purple-500"
                    />
                  </div>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Ikon (CSS-klass)
                  </label>
                  <input
                    name="icon"
                    type="text"
                    defaultValue={editingFlowType.icon || ''}
                    placeholder="t.ex. building, tree, car"
                    className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-purple-500"
                  />
                </div>
              </div>
              <div className="p-6 border-t bg-gray-50 flex justify-end gap-3">
                <button
                  type="button"
                  onClick={() => {
                    setEditingFlowType(null);
                    setIsNewFlowType(false);
                  }}
                  className="px-4 py-2 text-gray-700 border rounded-lg hover:bg-gray-100 transition-colors"
                >
                  Avbryt
                </button>
                <button
                  type="submit"
                  disabled={createFlowTypeMutation.isPending || updateFlowTypeMutation.isPending}
                  className="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 transition-colors
                           disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {createFlowTypeMutation.isPending || updateFlowTypeMutation.isPending
                    ? 'Sparar...'
                    : 'Spara'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Edit Category Modal */}
      {editingCategory && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full mx-4">
            <form onSubmit={handleSaveCategory}>
              <div className="p-6 border-b">
                <h3 className="text-lg font-semibold text-gray-900">
                  {isNewCategory ? 'Ny kategori' : 'Redigera kategori'}
                </h3>
              </div>
              <div className="p-6 space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Namn *
                  </label>
                  <input
                    name="name"
                    type="text"
                    defaultValue={editingCategory.name}
                    required
                    className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-purple-500"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Beskrivning
                  </label>
                  <textarea
                    name="description"
                    defaultValue={editingCategory.description || ''}
                    rows={2}
                    className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-purple-500"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Tjänstetyp
                  </label>
                  <select
                    name="flowTypeId"
                    defaultValue={editingCategory.flowTypeId || ''}
                    className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-purple-500"
                  >
                    <option value="">Ingen (fristående)</option>
                    {flowTypes.map((ft) => (
                      <option key={ft.id} value={ft.id}>
                        {ft.name}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Sortering
                  </label>
                  <input
                    name="sortOrder"
                    type="number"
                    defaultValue={editingCategory.sortOrder}
                    className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-purple-500"
                  />
                </div>
              </div>
              <div className="p-6 border-t bg-gray-50 flex justify-end gap-3">
                <button
                  type="button"
                  onClick={() => {
                    setEditingCategory(null);
                    setIsNewCategory(false);
                  }}
                  className="px-4 py-2 text-gray-700 border rounded-lg hover:bg-gray-100 transition-colors"
                >
                  Avbryt
                </button>
                <button
                  type="submit"
                  disabled={createCategoryMutation.isPending || updateCategoryMutation.isPending}
                  className="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 transition-colors
                           disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {createCategoryMutation.isPending || updateCategoryMutation.isPending
                    ? 'Sparar...'
                    : 'Spara'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
