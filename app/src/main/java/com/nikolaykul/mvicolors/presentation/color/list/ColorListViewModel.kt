package com.nikolaykul.mvicolors.presentation.color.list

import android.graphics.Color
import com.jakewharton.rxrelay2.PublishRelay
import com.nikolaykul.mvicolors.domain.color.ColorItem
import com.nikolaykul.mvicolors.domain.color.GetColorsUseCase
import com.nikolaykul.mvicolors.domain.navigation.DummyRouter
import com.nikolaykul.mvicolors.presentation.base.BaseViewModel
import com.nikolaykul.mvicolors.presentation.color.list.adapter.ColorListItem
import com.nikolaykul.mvicolors.presentation.utils.isActive
import com.nikolaykul.mvicolors.presentation.utils.randomError
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.random.Random

private const val FILTER_THRESHOLD_MILLIS = 200L

class ColorListViewModel @Inject constructor(
    private val getColorsUseCase: GetColorsUseCase,
    private val router: DummyRouter
) : BaseViewModel<ColorListState>(
    initState = ColorListState.NewItems(emptyList())
) {

    private var filterRelay = PublishRelay.create<String>()
    private var bottomItemsDisposable: Disposable? = null
    private var currentFilter: String? = null

    override fun onViewSubscribed() {
        loadInitColors()
        observeFilter()
    }

    fun onFilterChanged(newFilter: String) {
        filterRelay.accept(newFilter)
    }

    fun onFilterCancelled() {
        filterRelay.accept("")
    }

    fun onListEndReached() {
        if (bottomItemsDisposable.isActive()) {
            return
        }

        bottomItemsDisposable = getColorsUseCase.getColors(currentFilter)
            .map(this::mapToViewItems)
            .randomError()
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { nextState { ColorListState.Loading } }
            .safeSubscribe(
                onSuccess = { items ->
                    nextState { ColorListState.ExtraBottomItems(items) }
                },
                onError = ::onLoadingError
            )
    }

    fun onColorItemClicked(item: ColorListItem) {
        router.navigateToColorDetails(item.id)
    }

    fun onAddNewColorClicked() {
        router.navigateToAddColor()
    }

    private fun observeFilter() {
        filterRelay
            .debounce(FILTER_THRESHOLD_MILLIS, TimeUnit.MILLISECONDS)
            .distinctUntilChanged()
            .doOnNext { currentFilter = it }
            .switchMap {
                getColorsUseCase.getColors(it)
                    .map(this::mapToViewItems)
                    .randomError()
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnSubscribe { nextState { ColorListState.Loading } }
                    .doOnSuccess { items ->
                        nextState { ColorListState.NewItems(items) }
                    }
                    .doOnError(this::onLoadingError)
                    .toObservable()
            }
            .retry()
            .safeSubscribe()
    }

    private fun loadInitColors() {
        getColorsUseCase.getColors()
            .map(this::mapToViewItems)
            .randomError()
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { nextState { ColorListState.Loading } }
            .safeSubscribe(
                onSuccess = { items ->
                    nextState { ColorListState.NewItems(items) }
                },
                onError = ::onLoadingError
            )
    }

    private fun mapToViewItems(colors: List<ColorItem>) =
        colors.map {
            val color = Random(it.id).run {
                Color.rgb(nextInt(256), nextInt(256), nextInt(256))
            }
            ColorListItem(it.id, it.title, it.subTitle, color)
        }

    private fun onLoadingError(t: Throwable?) {
        nextState { ColorListState.Error(t?.localizedMessage) }
    }
}