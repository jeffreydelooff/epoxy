package com.airbnb.epoxy;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.airbnb.epoxy.EpoxyController.ModelInterceptorCallback;

import java.util.List;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Helper to bind data to a view using a builder style. The parameterized type should extend
 * Android's View.
 */
public abstract class EpoxyModel<T> {

  /**
   * Counts how many of these objects are created, so that each new object can have a unique id .
   * Uses negative values so that these autogenerated ids don't clash with database ids that may be
   * set with {@link #id(long)}
   */
  private static long idCounter = -1;

  /**
   * An id that can be used to uniquely identify this {@link EpoxyModel} for use in RecyclerView
   * stable ids. It defaults to a unique id for this object instance, if you want to maintain the
   * same id across instances use {@link #id(long)}
   */
  private long id;
  @LayoutRes private int layout;
  private boolean shown = true;
  /**
   * Set to true once this model is diffed in an adapter. Used to ensure that this model's id
   * doesn't change after being diffed.
   */
  boolean addedToAdapter;
  /**
   * The first controller this model was added to. A reference is kept in debug mode in order to run
   * validations. The model is allowed to be added to other controllers, but we only keep a
   * reference to the first.
   */
  private EpoxyController firstControllerAddedTo;
  /**
   * Models are staged when they are changed. This allows them to be automatically added when they
   * are done being changed (eg the next model is changed/added or buildModels finishes). It is only
   * allowed for AutoModels, and only if implicity adding is enabled.
   */
  EpoxyController controllerToStageTo;
  private boolean currentlyInInterceptors;
  private int hashCodeWhenAdded;
  private boolean hasDefaultId;
  @Nullable private SpanSizeOverrideCallback spanSizeOverride;

  protected EpoxyModel(long id) {
    id(id);
  }

  public EpoxyModel() {
    this(idCounter--);
    hasDefaultId = true;
  }

  boolean hasDefaultId() {
    return hasDefaultId;
  }

  /**
   * Get the view type to associate with this model in the recyclerview. For models that use a
   * layout resource, the view type is simply the layout resource value by default.
   * <p>
   * If this returns 0 Epoxy will assign a unique view type for this model at run time.
   *
   * @see androidx.recyclerview.widget.RecyclerView.Adapter#getItemViewType(int)
   */
  protected int getViewType() {
    return getLayout();
  }

  /**
   * Create and return a new instance of a view for this model. By default a view is created by
   * inflating the layout resource.
   */
  protected View buildView(@NonNull ViewGroup parent) {
    return LayoutInflater.from(parent.getContext()).inflate(getLayout(), parent, false);
  }

  /**
   * Binds the current data to the given view. You should bind all fields including unset/empty
   * fields to ensure proper recycling.
   */
  public void bind(@NonNull T view) {

  }

  /**
   * Similar to {@link #bind(Object)}, but provides a non null, non empty list of payloads
   * describing what changed. This is the payloads list specified in the adapter's notifyItemChanged
   * method. This is a useful optimization to allow you to only change part of a view instead of
   * updating the whole thing, which may prevent unnecessary layout calls. If there are no payloads
   * then {@link #bind(Object)} is called instead. This will only be used if the model is used with
   * an {@link EpoxyAdapter}
   */
  public void bind(@NonNull T view, @NonNull List<Object> payloads) {
    bind(view);
  }

  /**
   * Similar to {@link #bind(Object)}, but provides a non null model which was previously bound to
   * this view. This will only be called if the model is used with an {@link EpoxyController}.
   *
   * @param previouslyBoundModel This is a model with the same id that was previously bound. You can
   *                             compare this previous model with the current one to see exactly
   *                             what changed.
   *                             <p>
   *                             This model and the previously bound model are guaranteed to have
   *                             the same id, but will not necessarily be of the same type depending
   *                             on your implementation of {@link EpoxyController#buildModels()}.
   *                             With common usage patterns of Epoxy they should be the same type,
   *                             and will only differ if you are using different model classes with
   *                             the same id.
   *                             <p>
   *                             Comparing the newly bound model with the previous model allows you
   *                             to be more intelligent when binding your view. This may help you
   *                             optimize view binding, or make it easier to work with animations.
   *                             <p>
   *                             If the new model and the previous model have the same view type
   *                             (given by {@link EpoxyModel#getViewType()}), and if you are using
   *                             the default ReyclerView item animator, the same view will be
   *                             reused. This means that you only need to update the view to reflect
   *                             the data that changed. If you are using a custom item animator then
   *                             the view will be the same if the animator returns true in
   *                             canReuseUpdatedViewHolder.
   *                             <p>
   *                             This previously bound model is taken as a payload from the diffing
   *                             process, and follows the same general conditions for all
   *                             recyclerview change payloads.
   */
  public void bind(@NonNull T view, @NonNull EpoxyModel<?> previouslyBoundModel) {
    bind(view);
  }

  /**
   * Called when the view bound to this model is recycled. Subclasses can override this if their
   * view should release resources when it's recycled.
   * <p>
   * Note that {@link #bind(Object)} can be called multiple times without an unbind call in between
   * if the view has remained on screen to be reused across item changes. This means that you should
   * not rely on unbind to clear a view or model's state before bind is called again.
   *
   * @see EpoxyAdapter#onViewRecycled(EpoxyViewHolder)
   */
  public void unbind(@NonNull T view) {
  }

  public long id() {
    return id;
  }

  /**
   * Override the default id in cases where the data subject naturally has an id, like an object
   * from a database. This id can only be set before the model is added to the adapter, it is an
   * error to change the id after that.
   */
  public EpoxyModel<T> id(long id) {
    if ((addedToAdapter || firstControllerAddedTo != null) && id != this.id) {
      throw new IllegalEpoxyUsage(
          "Cannot change a model's id after it has been added to the adapter.");
    }

    hasDefaultId = false;
    this.id = id;
    return this;
  }

  /**
   * Use multiple numbers as the id for this model. Useful when you don't have a single long that
   * represents a unique id.
   * <p>
   * This hashes the numbers, so there is a tiny risk of collision with other ids.
   */
  public EpoxyModel<T> id(@NonNull Number... ids) {
    long result = 0;
    for (Number id : ids) {
      result = 31 * result + hashLong64Bit(id.hashCode());
    }
    return id(result);
  }

  /**
   * Use two numbers as the id for this model. Useful when you don't have a single long that
   * represents a unique id.
   * <p>
   * This hashes the two numbers, so there is a tiny risk of collision with other ids.
   */
  public EpoxyModel<T> id(long id1, long id2) {
    long result = hashLong64Bit(id1);
    result = 31 * result + hashLong64Bit(id2);
    return id(result);
  }

  /**
   * Use a string as the model id. Useful for models that don't clearly map to a numerical id. This
   * is preferable to using {@link String#hashCode()} because that is a 32 bit hash and this is a 64
   * bit hash, giving better spread and less chance of collision with other ids.
   * <p>
   * Since this uses a hashcode method to convert the String to a long there is a very small chance
   * that you may have a collision with another id. Assuming an even spread of hashcodes, and
   * several hundred models in the adapter, there would be roughly 1 in 100 trillion chance of a
   * collision. (http://preshing.com/20110504/hash-collision-probabilities/)
   *
   * @see EpoxyModel#hashString64Bit(CharSequence)
   */
  public EpoxyModel<T> id(@NonNull CharSequence key) {
    id(hashString64Bit(key));
    return this;
  }

  /**
   * Use several strings to define the id of the model.
   * <p>
   * Similar to {@link #id(CharSequence)}, but with additional strings.
   */
  public EpoxyModel<T> id(@NonNull CharSequence key, @NonNull CharSequence... otherKeys) {
    long result = hashString64Bit(key);
    for (CharSequence otherKey : otherKeys) {
      result = 31 * result + hashString64Bit(otherKey);
    }
    return id(result);
  }

  /**
   * Set an id that is namespaced with a string. This is useful when you need to show models of
   * multiple types, side by side and don't want to risk id collisions.
   * <p>
   * Since this uses a hashcode method to convert the String to a long there is a very small chance
   * that you may have a collision with another id. Assuming an even spread of hashcodes, and
   * several hundred models in the adapter, there would be roughly 1 in 100 trillion chance of a
   * collision. (http://preshing.com/20110504/hash-collision-probabilities/)
   *
   * @see EpoxyModel#hashString64Bit(CharSequence)
   * @see EpoxyModel#hashLong64Bit(long)
   */
  public EpoxyModel<T> id(@NonNull CharSequence key, long id) {
    long result = hashString64Bit(key);
    result = 31 * result + hashLong64Bit(id);
    id(result);
    return this;
  }

  /**
   * Hash a long into 64 bits instead of the normal 32. This uses a xor shift implementation to
   * attempt psuedo randomness so object ids have an even spread for less chance of collisions.
   * <p>
   * From http://stackoverflow.com/a/11554034
   * <p>
   * http://www.javamex.com/tutorials/random_numbers/xorshift.shtml
   */
  private static long hashLong64Bit(long value) {
    value ^= (value << 21);
    value ^= (value >>> 35);
    value ^= (value << 4);
    return value;
  }

  /**
   * Hash a string into 64 bits instead of the normal 32. This allows us to better use strings as a
   * model id with less chance of collisions. This uses the FNV-1a algorithm for a good mix of speed
   * and distribution.
   * <p>
   * Performance comparisons found at http://stackoverflow.com/a/1660613
   * <p>
   * Hash implementation from http://www.isthe.com/chongo/tech/comp/fnv/index.html#FNV-1a
   */
  private static long hashString64Bit(@NonNull CharSequence str) {
    long result = 0xcbf29ce484222325L;
    final int len = str.length();
    for (int i = 0; i < len; i++) {
      result ^= str.charAt(i);
      result *= 0x100000001b3L;
    }
    return result;
  }

  /**
   * Return the default layout resource to be used when creating views for this model. The resource
   * will be inflated to create a view for the model; additionally the layout int is used as the
   * views type in the RecyclerView.
   * <p>
   * This can be left unimplemented if you use the {@link EpoxyModelClass} annotation to define a
   * layout.
   * <p>
   * This default value can be overridden with {@link #layout(int)} at runtime to change the layout
   * dynamically.
   */
  @LayoutRes
  protected abstract int getDefaultLayout();

  @NonNull
  public EpoxyModel<T> layout(@LayoutRes int layoutRes) {
    onMutation();
    layout = layoutRes;
    return this;
  }

  @LayoutRes
  public final int getLayout() {
    if (layout == 0) {
      return getDefaultLayout();
    }

    return layout;
  }

  /**
   * Sets fields of the model to default ones.
   */
  @NonNull
  public EpoxyModel<T> reset() {
    onMutation();

    layout = 0;
    shown = true;

    return this;
  }

  /**
   * Add this model to the given controller. Can only be called from inside {@link
   * EpoxyController#buildModels()}.
   */
  public void addTo(@NonNull EpoxyController controller) {
    controller.addInternal(this);
  }

  /**
   * Add this model to the given controller if the condition is true. Can only be called from inside
   * {@link EpoxyController#buildModels()}.
   */
  public void addIf(boolean condition, @NonNull EpoxyController controller) {
    if (condition) {
      addTo(controller);
    } else if (controllerToStageTo != null) {
      // Clear this model from staging since it failed the add condition. If this model wasn't
      // staged (eg not changed before addIf was called, then we need to make sure to add the
      // previously staged model.
      controllerToStageTo.clearModelFromStaging(this);
      controllerToStageTo = null;
    }
  }

  /**
   * Add this model to the given controller if the {@link AddPredicate} return true. Can only be
   * called from inside {@link EpoxyController#buildModels()}.
   */
  public void addIf(@NonNull AddPredicate predicate, @NonNull EpoxyController controller) {
    addIf(predicate.addIf(), controller);
  }

  /**
   * @see #addIf(AddPredicate, EpoxyController)
   */
  public interface AddPredicate {
    boolean addIf();
  }

  /**
   * This is used internally by generated models to turn on validation checking when
   * "validateEpoxyModelUsage" is enabled and the model is used with an {@link EpoxyController}.
   */
  protected final void addWithDebugValidation(@NonNull EpoxyController controller) {
    if (controller == null) {
      throw new IllegalArgumentException("Controller cannot be null");
    }

    if (controller.isModelAddedMultipleTimes(this)) {
      throw new IllegalEpoxyUsage(
          "This model was already added to the controller at position "
              + controller.getFirstIndexOfModelInBuildingList(this));
    }

    if (firstControllerAddedTo == null) {
      firstControllerAddedTo = controller;

      // We save the current hashCode so we can compare it to the hashCode at later points in time
      // in order to validate that it doesn't change and enforce mutability.
      hashCodeWhenAdded = hashCode();

      // The one time it is valid to change the model is during an interceptor callback. To support
      // that we need to update the hashCode after interceptors have been run.
      // The model can be added to multiple controllers, but we only allow an interceptor change
      // the first time, since after that it will have been added to an adapter.
      controller.addAfterInterceptorCallback(new ModelInterceptorCallback() {
        @Override
        public void onInterceptorsStarted(EpoxyController controller) {
          currentlyInInterceptors = true;
        }

        @Override
        public void onInterceptorsFinished(EpoxyController controller) {
          hashCodeWhenAdded = EpoxyModel.this.hashCode();
          currentlyInInterceptors = false;
        }
      });
    }
  }

  boolean isDebugValidationEnabled() {
    return firstControllerAddedTo != null;
  }

  /**
   * This is used internally by generated models to do validation checking when
   * "validateEpoxyModelUsage" is enabled and the model is used with an {@link EpoxyController}.
   * This method validates that it is ok to change this model. It is only valid if the model hasn't
   * yet been added, or the change is being done from an {@link EpoxyController.Interceptor}
   * callback.
   * <p>
   * This is also used to stage the model for implicitly adding it, if it is an AutoModel and
   * implicit adding is enabled.
   */
  protected final void onMutation() {
    // The model may be added to multiple controllers, in which case if it was already diffed
    // and added to an adapter in one controller we don't want to even allow interceptors
    // from changing the model in a different controller
    if (isDebugValidationEnabled() && !currentlyInInterceptors) {
      throw new ImmutableModelException(this,
          getPosition(firstControllerAddedTo, this));
    }

    if (controllerToStageTo != null) {
      controllerToStageTo.setStagedModel(this);
    }
  }

  private static int getPosition(@NonNull EpoxyController controller,
      @NonNull EpoxyModel<?> model) {
    // If the model was added to multiple controllers, or was removed from the controller and then
    // modified, this won't be correct. But those should be very rare cases that we don't need to
    // worry about
    if (controller.isBuildingModels()) {
      return controller.getFirstIndexOfModelInBuildingList(model);
    }

    return controller.getAdapter().getModelPosition(model);
  }

  /**
   * This is used internally by generated models to do validation checking when
   * "validateEpoxyModelUsage" is enabled and the model is used with a {@link EpoxyController}. This
   * method validates that the model's hashCode hasn't been changed since it was added to the
   * controller. This is similar to {@link #onMutation()}, but that method is only used for
   * specific model changes such as calling a setter. By checking the hashCode, this method allows
   * us to catch more subtle changes, such as through setting a field directly or through changing
   * an object that is set on the model.
   */
  protected final void validateStateHasNotChangedSinceAdded(String descriptionOfChange,
      int modelPosition) {
    if (isDebugValidationEnabled()
        && !currentlyInInterceptors
        && hashCodeWhenAdded != hashCode()) {
      throw new ImmutableModelException(this, descriptionOfChange, modelPosition);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof EpoxyModel)) {
      return false;
    }

    EpoxyModel<?> that = (EpoxyModel<?>) o;

    if (id != that.id) {
      return false;
    }
    if (getViewType() != that.getViewType()) {
      return false;
    }
    return shown == that.shown;
  }

  @Override
  public int hashCode() {
    int result = (int) (id ^ (id >>> 32));
    result = 31 * result + getViewType();
    result = 31 * result + (shown ? 1 : 0);
    return result;
  }

  /**
   * Subclasses can override this if they want their view to take up more than one span in a grid
   * layout.
   *
   * @param totalSpanCount The number of spans in the grid
   * @param position       The position of the model
   * @param itemCount      The total number of items in the adapter
   */
  public int getSpanSize(int totalSpanCount, int position, int itemCount) {
    return 1;
  }

  public EpoxyModel<T> spanSizeOverride(@Nullable SpanSizeOverrideCallback spanSizeCallback) {
    this.spanSizeOverride = spanSizeCallback;
    return this;
  }

  public interface SpanSizeOverrideCallback {
    int getSpanSize(int totalSpanCount, int position, int itemCount);
  }

  int getSpanSizeInternal(int totalSpanCount, int position, int itemCount) {
    if (spanSizeOverride != null) {
      return spanSizeOverride.getSpanSize(totalSpanCount, position, itemCount);
    }

    return getSpanSize(totalSpanCount, position, itemCount);
  }

  /**
   * Change the visibility of the model so that it's view is shown. This only works if the model is
   * used in {@link EpoxyAdapter} or a {@link EpoxyModelGroup}, but is not supported in {@link
   * EpoxyController}
   */
  @NonNull
  public EpoxyModel<T> show() {
    return show(true);
  }

  /**
   * Change the visibility of the model's view. This only works if the model is
   * used in {@link EpoxyAdapter} or a {@link EpoxyModelGroup}, but is not supported in {@link
   * EpoxyController}
   */
  @NonNull
  public EpoxyModel<T> show(boolean show) {
    onMutation();
    shown = show;
    return this;
  }

  /**
   * Change the visibility of the model so that it's view is hidden. This only works if the model is
   * used in {@link EpoxyAdapter} or a {@link EpoxyModelGroup}, but is not supported in {@link
   * EpoxyController}
   */
  @NonNull
  public EpoxyModel<T> hide() {
    return show(false);
  }

  /**
   * Whether the model's view should be shown on screen. If false it won't be inflated and drawn,
   * and will be like it was never added to the recycler view.
   */
  public boolean isShown() {
    return shown;
  }

  /**
   * Whether the adapter should save the state of the view bound to this model.
   */
  public boolean shouldSaveViewState() {
    return false;
  }

  /**
   * Called if the RecyclerView failed to recycle this model's view. You can take this opportunity
   * to clear the animation(s) that affect the View's transient state and return <code>true</code>
   * so that the View can be recycled. Keep in mind that the View in question is already removed
   * from the RecyclerView.
   *
   * @return True if the View should be recycled, false otherwise
   * @see EpoxyAdapter#onFailedToRecycleView(androidx.recyclerview.widget.RecyclerView.ViewHolder)
   */
  public boolean onFailedToRecycleView(@NonNull T view) {
    return false;
  }

  /**
   * Called when this model's view is attached to the window.
   *
   * @see EpoxyAdapter#onViewAttachedToWindow(androidx.recyclerview.widget.RecyclerView.ViewHolder)
   */
  public void onViewAttachedToWindow(@NonNull T view) {

  }

  /**
   * Called when this model's view is detached from the the window.
   *
   * @see
   * EpoxyAdapter#onViewDetachedFromWindow(androidx.recyclerview.widget.RecyclerView.ViewHolder)
   */
  public void onViewDetachedFromWindow(@NonNull T view) {

  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{"
        + "id=" + id
        + ", viewType=" + getViewType()
        + ", shown=" + shown
        + ", addedToAdapter=" + addedToAdapter
        + '}';
  }
}
